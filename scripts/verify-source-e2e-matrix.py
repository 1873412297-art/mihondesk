"""Source end-to-end matrix runner (Workstream 1A).

For each source in the input manifest: locate the .mext package, load it into
the real extension host over stdio IPC (struct big-endian 4-byte length + JSON,
same protocol as scripts/probe-installed-sources.py), then run the chain:

    install/load -> browse -> search -> detail -> chapters -> pages -> image

Results (per-step status, duration, error, attribution) are written as JSON;
a Markdown matrix (源/类型/安装/搜索/详情/章节/页面/图片/结论/归因) is rendered
next to the JSON output.

Failure attribution labels (mandatory on every non-pass step):
    implementation_defect | shim_limit | site_block | manual_interaction | environment

Input is JSON, or YAML if the file ends in .yaml/.yml and PyYAML is installed.

Usage:
    python scripts/verify-source-e2e-matrix.py \
        --exe <mihondesk.exe> \
        --sources scripts/source-e2e-matrix-sample.json \
        --output build/source-e2e-matrix-output.json

Never writes user data; each source runs in a fresh temp working dir.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import socket
import ssl
import struct
import subprocess
import sys
import tempfile
import threading
import time
import traceback
import uuid
import zipfile
import concurrent.futures

STEPS = ['install', 'browse', 'search', 'detail', 'chapters', 'pages', 'image']
CORE_STEPS = ['browse', 'search', 'detail', 'chapters', 'pages', 'image']

# Attribution vocabulary (fixed by the workstream spec).
ATTRIBUTIONS = ['implementation_defect', 'shim_limit', 'site_block',
                'manual_interaction', 'environment']

# Default error-signature -> attribution rules, applied in order.
# Each entry: (regex on lowercased error text, attribution, blocked)
_DEFAULT_HINTS = [
    (r'webview|captcha|challenge|javascript required|verify you are human', 'site_block', False),
    (r'login required|not logged in|unauthor|401|forbidden.*login|session expired|cookie', 'manual_interaction', True),
    (r'403|forbidden|cloudflare|access denied|blocked|banned|too many requests|429', 'site_block', False),
    (r'404|not found', 'implementation_defect', False),
    (r'\.mext|package .* not found|no such file', 'environment', False),
    (r'quickjs|shim|unsupported (api|operation)|not implemented|activity stub|android\.', 'shim_limit', False),
    (r'filter\$|instantiationerror|noclassdeffounderror|classnotfound', 'shim_limit', False),
    (r'timed? ?out|timeout', 'environment', False),
    (r'connection|socket|dns|resolve|unreachable|network|ssl|tls|certificate', 'environment', False),
]

_VERDICTS = {
    'pass': '通过',
    'limited_pass': '受限通过',
    'unsupported': '不支持',
    'not_run': '未执行',
}

_IMAGE_MAGICS = [
    (b'\x89PNG', 'png'), (b'\xff\xd8\xff', 'jpeg'), (b'GIF8', 'gif'),
    (b'RIFF', 'webp'), (b'BM', 'bmp'), (b'II*\x00', 'tiff'), (b'MM\x00*', 'tiff'),
]


def load_manifest(path):
    text = Path(path).read_text(encoding='utf-8')
    if str(path).lower().endswith(('.yaml', '.yml')):
        try:
            import yaml  # type: ignore
        except ImportError:
            sys.exit('PyYAML is required for YAML input; use JSON or: pip install pyyaml')
        data = yaml.safe_load(text)
    else:
        data = json.loads(text)
    sources = data['sources'] if isinstance(data, dict) else data
    for entry in sources:
        if 'name' not in entry:
            sys.exit('every source entry needs a "name": %r' % (entry,))
        if 'query' in entry and 'queries' not in entry:
            entry['queries'] = [entry['query']] if isinstance(entry['query'], str) else list(entry['query'])
    return sources


def classify(error, source):
    """Map an exception to (attribution, blocked) using per-source hints then defaults."""
    text = str(error).lower()
    for hint in source.get('attribution_hints', []):
        if re.search(hint['pattern'], text, re.IGNORECASE):
            return hint['attribution'], bool(hint.get('blocked'))
    for pattern, attribution, blocked in _DEFAULT_HINTS:
        if re.search(pattern, text):
            if attribution == 'manual_interaction' and not source.get('needs_login'):
                attribution = 'site_block'
            return attribution, blocked
    if source.get('needs_login'):
        return 'manual_interaction', True
    return 'implementation_defect', False


# Hop-by-hop / transport-managed headers the broker must not forward verbatim;
# OkHttp manages these itself and so does this probe (mirrors DesktopNetworkHelper).
_DROP_REQUEST_HEADERS = {'host', 'content-length', 'connection', 'accept-encoding',
                         'proxy-connection', 'transfer-encoding', 'upgrade'}

_MAX_BROKER_INLINE = 4 * 1024 * 1024   # mirror DesktopNetworkHelper.MAX_INLINE_BYTES
_MAX_BROKER_BYTES = 64 * 1024 * 1024   # mirror DesktopNetworkHelper.MAX_RESPONSE_BYTES
_BROKER_FILE_THRESHOLD = 3 * 1024 * 1024  # keep frames small; large bodies go via bodyFileName


class HostClient:
    """stdio IPC to the extension host: 4-byte big-endian length + JSON frames.

    Bidirectional: besides answering [IpcRequest]s, the sandboxed extension host
    brokers every HTTP call back to the parent via [IpcCallbackRequest] frames
    (callbackType "broker_http", see IpcContracts.kt / BrokeredHttpClient.kt).
    A dedicated reader thread dispatches both frame types; HTTP callbacks are
    executed inline and answered with [IpcCallbackResponse] frames.
    """

    def __init__(self, exe, workdir, timeout):
        self.timeout = timeout
        self.request_id = 0
        self.workdir = Path(workdir)
        self.pending = {}          # requestId -> Future for in-flight IpcRequest
        self.write_lock = threading.Lock()
        self.reader_error = None
        self.stderr_path = Path(workdir) / 'host-stderr.log'
        err = open(self.stderr_path, 'w')
        self.proc = subprocess.Popen([exe, '--extension-host', '--stdio'],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=err, cwd=workdir)
        self.err_file = err
        self.reader = threading.Thread(target=self._reader_loop, name='ipc-reader', daemon=True)
        self.reader.start()

    # ---- frame plumbing -------------------------------------------------

    def _send_frame(self, frame_type, payload):
        msg = dict(payload)
        msg['type'] = frame_type
        data = json.dumps(msg).encode()
        with self.write_lock:
            self.proc.stdin.write(struct.pack('>I', len(data)) + data)
            self.proc.stdin.flush()

    def _reader_loop(self):
        try:
            while True:
                prefix = self.proc.stdout.read(4)
                if len(prefix) != 4:
                    raise RuntimeError('extension host closed stdout (crashed?)')
                count = struct.unpack('>I', prefix)[0]
                frame = json.loads(self.proc.stdout.read(count))
                self._dispatch(frame)
        except Exception as exc:  # reader death fails all pending calls
            self.reader_error = exc
            for future in list(self.pending.values()):
                future.set_exception(exc)
            self.pending.clear()

    def _dispatch(self, frame):
        frame_type = frame.get('type', '')
        if frame_type.endswith('IpcResponse'):
            future = self.pending.pop(frame.get('requestId'), None)
            if future is not None:
                future.set_result(frame)
            # A late answer after a client-side timeout has no waiter; drop it.
        elif frame_type.endswith('IpcCallbackRequest'):
            if frame.get('callbackType') == 'broker_http':
                self._answer_broker_http(frame)
            else:
                self._send_frame('mihon.extension.ipc.IpcCallbackResponse', {
                    'requestId': frame.get('requestId'),
                    'success': False,
                    'error': 'unsupported callbackType %r in probe client' % frame.get('callbackType'),
                })
        # IpcCancel and unknown frames: logged by the host itself; nothing to answer.

    def call(self, command, payload):
        self.request_id += 1
        request_id = self.request_id
        future = concurrent.futures.Future()
        self.pending[request_id] = future
        try:
            self._send_frame('mihon.extension.ipc.IpcRequest', {
                'requestId': request_id, 'command': command,
                'payloadJson': json.dumps(payload)})
        except Exception:
            self.pending.pop(request_id, None)
            raise
        res = future.result(timeout=self.timeout)
        if not res.get('success'):
            raise RuntimeError(res.get('error', str(res)))
        return json.loads(res['payloadJson'])

    # ---- brokered HTTP (callback side of the IPC) ------------------------

    def _answer_broker_http(self, frame):
        request_id = frame.get('requestId')
        try:
            request = json.loads(frame.get('payloadJson') or '{}')
            response = self._execute_broker_request(request)
            payload = {'requestId': request_id, 'success': True,
                       'payloadJson': json.dumps(response)}
            self._log_broker_exchange(request, response)
        except Exception as exc:
            payload = {'requestId': request_id, 'success': False,
                       'error': 'probe broker_http handler failed: %s' % exc}
            self._log_broker_exchange(request, {'statusCode': 0, 'failureKind': 'PROBE_HANDLER_ERROR',
                                                'error': str(exc)[:200]})
        try:
            self._send_frame('mihon.extension.ipc.IpcCallbackResponse', payload)
        except Exception:
            pass  # host is gone; nothing actionable

    def _log_broker_exchange(self, request, response):
        """Debug aid: append one JSON line per brokered request when the env var names a file."""
        import os as _os
        path = _os.environ.get('MIHON_E2E_BROKER_LOG')
        if not path:
            return
        try:
            line = {'method': request.get('method'), 'url': request.get('url'),
                    'status': response.get('statusCode'), 'failureKind': response.get('failureKind'),
                    'error': (response.get('error') or '')[:160]}
            import io
            with io.open(path, 'a', encoding='utf-8') as fh:
                fh.write(json.dumps(line, ensure_ascii=False) + '\n')
        except Exception:
            pass

    def _execute_broker_request(self, request):
        method = (request.get('method') or 'GET').upper()
        url = request.get('url') or ''
        headers = {}
        for key, value in (request.get('headers') or {}).items():
            headers[key.lower()] = value
        for key, values in (request.get('headerValues') or {}).items():
            if values:
                headers[key.lower()] = ', '.join(str(v) for v in values)
        body_bytes = None
        if request.get('bodyBase64'):
            import base64
            body_bytes = base64.b64decode(request['bodyBase64'])
        elif request.get('body') is not None:
            body_bytes = str(request['body']).encode('utf-8')
        return self._http_exchange(method, url, headers, body_bytes)

    def _http_exchange(self, method, url, headers, body_bytes):
        import urllib.error
        import urllib.request
        hop_headers = {k: v for k, v in headers.items() if k not in _DROP_REQUEST_HEADERS}
        redirects_left = 5
        current_method, current_body = method, body_bytes
        try:
            while True:
                req = urllib.request.Request(url, data=current_body, method=current_method)
                for key, value in hop_headers.items():
                    req.add_header(key, value)
                try:
                    with urllib.request.urlopen(req, timeout=min(self.timeout, 90)) as resp:
                        return self._build_broker_response(resp.status, resp.headers, resp.read(),
                                                           resp.geturl())
                except urllib.error.HTTPError as http_error:
                    if (http_error.code in (301, 302, 303, 307, 308) and redirects_left > 0 and
                            http_error.headers.get('Location')):
                        # Mirror OkHttp: 307/308 keep the method; the rest downgrade to GET.
                        redirects_left -= 1
                        location = http_error.headers.get('Location')
                        url = urllib.parse.urljoin(url, location)
                        if http_error.code not in (307, 308):
                            current_method, current_body = 'GET', None
                        continue
                    return self._build_broker_response(
                        http_error.code, http_error.headers,
                        http_error.read() if http_error.fp else b'', url)
        except urllib.error.URLError as exc:
            reason = exc.reason if hasattr(exc, 'reason') else exc
            return self._failure_broker_response(reason)
        except (socket.timeout, TimeoutError) as exc:
            return self._failure_broker_response(exc, force_kind='TIMEOUT')
        except ssl.SSLError as exc:
            return self._failure_broker_response(exc, force_kind='TLS')
        except ValueError as exc:
            return {'statusCode': 400, 'error': 'INVALID_REQUEST: %s' % exc,
                    'failureKind': 'INVALID_REQUEST'}

    def _build_broker_response(self, status, headers, body, final_url):
        header_values = {}
        try:
            items = list(headers.raw_items())
        except AttributeError:
            items = list(headers.items())
        for key, value in items:
            header_values.setdefault(key, []).append(value)
        flat_headers = {key: ', '.join(values) for key, values in header_values.items()}
        kind = self._classify_failure(status, header_values, body)
        response = {
            'statusCode': status,
            'headers': flat_headers,
            'headerValues': header_values,
            'finalUrl': final_url,
            'failureKind': kind,
        }
        if len(body) > _MAX_BROKER_BYTES:
            return {'statusCode': 502, 'error': 'probe broker response exceeds 64 MiB',
                    'failureKind': 'HTTP_ERROR'}
        if len(body) > _BROKER_FILE_THRESHOLD:
            # Large binary payloads travel via a host-side file (see
            # BrokeredHttpClient.materialize): name must match broker-<uuid>.bin
            # and live under <workdir>/broker-responses.
            directory = self.workdir / 'broker-responses'
            directory.mkdir(exist_ok=True)
            name = 'broker-%s.bin' % uuid.uuid4()
            (directory / name).write_bytes(body)
            response['bodyFileName'] = name
        else:
            import base64
            response['bodyBase64'] = base64.b64encode(body).decode()
            if len(body) <= _MAX_BROKER_INLINE:
                response['body'] = body.decode('utf-8', errors='replace')
        return response

    @staticmethod
    def _classify_failure(status, header_values, body):
        """Mirror DesktopNetworkHelper.NetworkResponse.failureKind()."""
        preview = body[:8192].decode('utf-8', errors='replace').lower()
        lowered = {k.lower(): [str(v).lower() for v in vals] for k, vals in header_values.items()}
        if status == 429:
            return 'RATE_LIMITED'
        if status == 403 and 'cloudflare' in preview and (
                'sorry, you have been blocked' in preview or 'error code: 1020' in preview):
            return 'SITE_BLOCKED'
        if status in (403, 503) and (
                any('challenge' in v for v in lowered.get('cf-mitigated', [])) or
                'cf-chl-' in preview or 'challenge-platform' in preview or 'captcha' in preview):
            return 'WEB_VERIFICATION'
        if status in (401, 403):
            return 'AUTHENTICATION_REQUIRED'
        if status not in range(200, 400):
            return 'HTTP_ERROR'
        return None

    @staticmethod
    def _failure_broker_response(reason, force_kind=None):
        text = str(reason)
        lowered = text.lower()
        if force_kind:
            kind = force_kind
        elif isinstance(reason, socket.gaierror):
            kind = 'OFFLINE'
        elif isinstance(reason, ConnectionRefusedError):
            kind = 'PROXY' if 'proxy' in lowered else 'CONNECTION'
        elif isinstance(reason, (socket.timeout, TimeoutError)):
            kind = 'TIMEOUT'
        elif isinstance(reason, ssl.SSLError):
            kind = 'TLS'
        else:
            kind = 'CONNECTION'
        return {'statusCode': 502, 'error': '%s: %s' % (kind, text[:300]),
                'failureKind': kind}

    def close(self):
        try:
            self.proc.kill()
            self.proc.wait(timeout=10)
        except Exception:
            pass
        self.err_file.close()


def find_package(source, roots):
    """Locate the .mext for a source: match `package` (or name) against filenames."""
    needle = source.get('package') or source['name']
    needle = needle.lower()
    for root in roots:
        root = Path(root)
        if not root.is_dir():
            continue
        for pkg in sorted(root.rglob('*.mext')):
            if needle in pkg.name.lower():
                return pkg
    return None


def pick_source_id(sources, preferred_lang=('zh', 'en', 'all')):
    for lang in preferred_lang:
        hit = next((s for s in sources if s.get('lang') == lang), None)
        if hit:
            return hit
    return sources[0]


def run_source(source, exe, roots, timeout, evidence_dir, download_images):
    steps = []
    state = {}

    def record(step, status, attribution=None, blocked=False, error=None, evidence=None):
        steps.append({'step': step, 'status': 'blocked' if blocked else status,
                      'attribution': attribution, 'duration_ms': evidence.pop('_ms', None)
                      if evidence and '_ms' in evidence else None,
                      'error': error, 'evidence': evidence})
        return status == 'pass'

    def fail(step, exc, elapsed):
        attribution, blocked = classify(exc, source)
        record(step, 'fail', attribution, blocked,
               error=(str(exc) or type(exc).__name__)[:400],
               evidence={'_ms': elapsed})

    def run(step, fn):
        started = time.monotonic()
        try:
            evidence = fn() or {}
            elapsed = int((time.monotonic() - started) * 1000)
            if '_skipped' in evidence:
                reason = evidence.pop('_skipped')
                record(step, 'skipped', evidence={'_ms': elapsed, 'reason': reason})
                return False
            evidence['_ms'] = elapsed
            record(step, 'pass', evidence=evidence)
            return True
        except Exception as exc:
            fail(step, exc, int((time.monotonic() - started) * 1000))
            return False

    work = Path(tempfile.mkdtemp(prefix='mihon-e2e-%s-' % re.sub(r'[^A-Za-z0-9_.-]', '_', source['name'])))
    client = None
    try:
        client = HostClient(exe, work, timeout)

        def step_install():
            pkg = find_package(source, roots)
            if pkg is None:
                raise FileNotFoundError('no .mext matching %r under %s' %
                                        (source.get('package') or source['name'], roots))
            # The host process runs with its own cwd; package and working paths
            # must be absolute or load_extension resolves them against the wrong root.
            pkg = Path(pkg).resolve()
            with zipfile.ZipFile(pkg) as z:
                manifest = json.loads(z.read('manifest.json'))
            workdir = work / pkg.stem
            workdir.mkdir(exist_ok=True)
            runtime = client.call('load_extension', {'packagePath': str(pkg), 'workingDir': str(workdir)})
            state['sources'] = runtime
            state['source'] = pick_source_id(runtime)
            return {'package': pkg.name, 'manifestSources': len(manifest['sources']),
                    'runtimeSources': len(runtime), 'selected': state['source']['id']}

        def step_browse():
            popular = client.call('get_popular', {'sourceId': state['source']['id'], 'page': 1})
            mangas = popular['mangas']
            if not mangas:
                raise RuntimeError('get_popular returned 0 mangas')
            state['manga'] = mangas[0]
            return {'count': len(mangas), 'firstUrl': state['manga']['url'][:200]}

        def step_search():
            queries = source.get('queries') or ['']
            counts, first_error = {}, None
            for q in queries:
                try:
                    res = client.call('search_manga', {'sourceId': state['source']['id'], 'page': 1, 'query': q})
                    counts[q] = len(res['mangas'])
                    if not state.get('manga') and res['mangas']:
                        state['manga'] = res['mangas'][0]
                except Exception as exc:
                    counts[q] = -1
                    first_error = first_error or exc
            hits = sum(1 for c in counts.values() if c > 0)
            if hits == 0 and first_error is not None:
                raise first_error
            if hits == 0 and queries != ['']:
                raise RuntimeError('all queries returned 0 results: %r' % counts)
            return {'queries': counts}

        def step_detail():
            manga = client.call('get_manga_details', {'sourceId': state['source']['id'],
                                                      'mangaJson': json.dumps(state['manga'])})
            state['manga'] = manga
            return {'title': (manga.get('title') or '')[:120], 'hasDescription': bool(manga.get('description'))}

        def step_chapters():
            chapters = client.call('get_chapter_list', {'sourceId': state['source']['id'],
                                                        'mangaJson': json.dumps(state['manga'])})
            if not chapters:
                raise RuntimeError('get_chapter_list returned 0 chapters')
            state['chapter'] = next((c for c in chapters if '\U0001f512' not in c['name']), chapters[-1])
            return {'count': len(chapters), 'sample': state['chapter']['name'][:120]}

        def step_pages():
            pages = client.call('get_page_list', {'sourceId': state['source']['id'],
                                                  'chapterJson': json.dumps(state['chapter'])})
            if not pages:
                raise RuntimeError('get_page_list returned 0 pages')
            state['page'] = pages[0]
            return {'count': len(pages), 'firstIndex': pages[0]['index']}

        def step_image():
            if not download_images:
                return {'_skipped': 'image download disabled (--no-images)'}
            image = client.call('get_image', {'sourceId': state['source']['id'], 'page': state['page']})
            file_name = image.get('fileName') if isinstance(image, dict) else None
            if not file_name:
                raise RuntimeError('host returned no image file name (source likely does not '
                                   'implement WindowsImageSource or image fetch failed)')
            image_file = work / 'page-images' / file_name
            data = image_file.read_bytes()
            if not data:
                raise RuntimeError('downloaded image is 0 bytes')
            kind = next((k for magic, k in _IMAGE_MAGICS if data[:len(magic)] == magic), 'unknown')
            image_file.unlink()
            return {'bytes': len(data), 'format': kind, 'signature': data[:12].hex()}

        for step, fn in [('install', step_install), ('browse', step_browse), ('search', step_search),
                         ('detail', step_detail), ('chapters', step_chapters), ('pages', step_pages),
                         ('image', step_image)]:
            if step != 'install' and not any(s['step'] == 'install' and s['status'] == 'pass' for s in steps):
                install_step = next((s for s in steps if s['step'] == 'install'), None)
                record(step, 'blocked', (install_step or {}).get('attribution') or 'environment', True,
                       error='install step did not pass; chain aborted')
                continue
            # detail..image depend on a selected manga; browse and search are independent probes.
            upstream = [s for s in steps if s['step'] in ('browse', 'search', 'detail', 'chapters', 'pages')
                        and s['step'] not in ('browse', 'search') and s['status'] != 'pass']
            needs_manga = step in ('detail', 'chapters')
            needs_manga_strict = step in ('pages', 'image')
            if upstream and (step not in ('browse', 'search') or needs_manga or needs_manga_strict):
                record(step, 'blocked', upstream[0]['attribution'], upstream[0]['status'] == 'blocked',
                       error='upstream step %s %s; chain aborted' % (upstream[0]['step'], upstream[0]['status']))
                continue
            if needs_manga and not state.get('manga'):
                probe_fail = next((s for s in steps if s['step'] in ('browse', 'search') and s['status'] != 'pass'), None)
                record(step, 'blocked', (probe_fail or {}).get('attribution') or 'environment', True,
                       error='no manga selected by browse/search' + (' (upstream %s %s)' % (probe_fail['step'], probe_fail['status']) if probe_fail else ''))
                continue
            if needs_manga_strict and not (state.get('manga') and state.get('chapter')):
                probe_fail = next((s for s in steps if s['step'] in ('browse', 'search') and s['status'] != 'pass'), None)
                record(step, 'blocked', (probe_fail or {}).get('attribution') or 'environment', True,
                       error='no chapter selected' + (' (upstream %s %s)' % (probe_fail['step'], probe_fail['status']) if probe_fail else ''))
                continue
            run(step, fn)
    except Exception as exc:
        # Host-level failure (spawn error / mid-chain crash).
        if not steps:
            fail('install', exc, 0)
            for step in STEPS[1:]:
                record(step, 'blocked', 'environment', True,
                       error='host unavailable: %s' % (str(exc)[:200],))
        else:
            attribution, _ = classify(exc, source)
            record('host', 'fail', attribution, False, error=str(exc)[:400],
                   evidence={'traceback': traceback.format_exc()[-1000:]})
    finally:
        if client:
            client.close()

    # Persist host stderr as raw evidence.
    stderr_src = work / 'host-stderr.log'
    evidence_path = None
    if stderr_src.exists():
        evidence_dir.mkdir(parents=True, exist_ok=True)
        safe = re.sub(r'[^A-Za-z0-9_.-]', '_', source['name'])
        evidence_path = evidence_dir / ('%s.host-stderr.log' % safe)
        evidence_path.write_bytes(stderr_src.read_bytes())
    shutil.rmtree(work, ignore_errors=True)

    # Verdict.
    failures = [s for s in steps if s['status'] == 'fail']
    blocks = [s for s in steps if s['status'] == 'blocked']
    skipped = [s for s in steps if s['status'] == 'skipped']
    if failures:
        attribution = failures[0]['attribution']
        # manual_interaction failure demotes to 受限通过 per the workstream rules;
        # everything else that fails is 不支持 (shim_limit / site_block / environment
        # are disclosure grades, not passes).
        verdict = 'limited_pass' if attribution == 'manual_interaction' else 'unsupported'
    elif blocks:
        attribution = blocks[0]['attribution']
        verdict = 'limited_pass' if attribution == 'manual_interaction' else 'unsupported'
    elif skipped:
        # Operator-disabled steps (e.g. --no-images) mean the full chain was not
        # exercised: disclose as 受限通过, never as a clean 通过.
        verdict = 'limited_pass'
        attribution = None
    else:
        verdict = 'pass'
        attribution = None

    return {
        'name': source['name'], 'type': source.get('type', ''),
        'needs_login': bool(source.get('needs_login')), 'notes': source.get('notes', ''),
        'verdict': verdict, 'attribution': attribution,
        'steps': [{k: v for k, v in s.items() if v is not None} for s in steps],
        'evidence_path': str(evidence_path) if evidence_path else None,
    }


_MARK = {'pass': '✅', 'fail': '❌', 'blocked': '⛔', 'skipped': '⏭️'}

# Markdown matrix columns -> underlying step(s). The spec's 安装 column carries
# both install and browse (install/browse shown as `install/browse`).
_MD_COLUMNS = [
    ('安装', ['install', 'browse']),
    ('搜索', ['search']),
    ('详情', ['detail']),
    ('章节', ['chapters']),
    ('页面', ['pages']),
    ('图片', ['image']),
]


def _cell(result, step):
    s = next((x for x in result['steps'] if x['step'] == step), None)
    if s is None:
        return '—'
    if s['status'] == 'pass':
        return _MARK['pass']
    return '%s `%s`' % (_MARK[s['status']], s['attribution'])


def render_markdown(results, exe, manifest_path):
    lines = [
        '# 图源端到端矩阵（Workstream 1A）',
        '',
        '- 生成：%s' % time.strftime('%Y-%m-%d %H:%M:%S'),
        '- 宿主：`%s`' % exe,
        '- 源清单：`%s`' % manifest_path,
        '- 状态：✅ pass　❌ fail　⛔ blocked　⏭️ skipped（操作者禁用，不计入通过）　归因：`implementation_defect` / `shim_limit` / `site_block` / `manual_interaction` / `environment`',
        '- 「安装」列格式为 `安装/浏览` 两步的合并标记；逐步明细见同名 JSON。',
        '',
        '| 源 | 类型 | 安装/浏览 | 搜索 | 详情 | 章节 | 页面 | 图片 | 结论 | 归因 |',
        '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |',
    ]
    for r in results:
        cells = ['/'.join(_cell(r, step) for step in steps) for _, steps in _MD_COLUMNS]
        lines.append('| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |' % (
            r['name'], r['type'], *cells, _VERDICTS[r['verdict']],
            ('`%s`' % r['attribution']) if r['attribution'] else '—'))
    notes = [(r['name'], r['notes']) for r in results if r['notes']]
    if notes:
        lines += ['', '## 备注', '']
        for name, note in notes:
            lines.append('- **%s**：%s' % (name, note))
    lines.append('')
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(
        description='Source end-to-end matrix runner (Workstream 1A). For each source: '
                    'install/load -> browse -> search -> detail -> chapters -> pages -> image, '
                    'with mandatory failure attribution. See the module docstring for details.',
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--exe', required=True, help='path to mihondesk.exe (extension host mode)')
    parser.add_argument('--sources', required=True, help='source manifest (JSON, or YAML with PyYAML)')
    parser.add_argument('--output', required=True, help='JSON output path')
    parser.add_argument('--extensions-dir', action='append', default=None,
                        help='extra .mext search roots (repeatable); the per-user '
                             'MihonW extensions directory under APPDATA is always searched')
    parser.add_argument('--timeout', type=int, default=150, help='per-IPC-call timeout in seconds')
    parser.add_argument('--no-images', action='store_true', help='skip the image download step')
    parser.add_argument('--only', default='', help='run only sources whose name contains this substring')
    args = parser.parse_args()

    exe = str(Path(args.exe).resolve())
    if not Path(exe).is_file():
        sys.exit('extension host exe not found: %s' % exe)

    manifest = load_manifest(args.sources)
    if args.only:
        manifest = [s for s in manifest if args.only.lower() in s['name'].lower()]

    roots = list(args.extensions_dir or [])
    roots.append(Path(os.environ.get('APPDATA', '')) / 'MihonW' / 'extensions')

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    evidence_dir = output.parent / (output.stem + '-evidence')

    results = [run_source(s, exe, roots, args.timeout, evidence_dir, not args.no_images)
               for s in manifest]

    payload = {
        'generatedAt': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
        'exe': exe, 'manifest': str(args.sources),
        'attributionVocabulary': ATTRIBUTIONS,
        'steps': STEPS,
        'results': results,
    }
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding='utf-8')

    md_path = output.with_suffix('.md')
    md_path.write_text(render_markdown(results, exe, args.sources), encoding='utf-8')

    for r in results:
        marks = ' '.join('%s=%s' % (x['step'], x['status']) for x in r['steps'])
        print('%-14s %-6s %-8s %s%s' % (r['name'], r['verdict'],
                                        r['attribution'] or '-', marks,
                                        ('  notes: ' + r['notes']) if r['notes'] else ''), flush=True)
    print('JSON: %s' % output)
    print('Markdown: %s' % md_path)

    sys.exit(1 if any(r['verdict'] in ('unsupported',) for r in results) else 0)


if __name__ == '__main__':
    main()
