package mihon.desktop.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CategoryIdListFieldTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `typing a separator does not merge the next id into the previous one`() = runComposeUiTest {
        var selected = emptySet<Long>()
        setContent {
            var value by remember { mutableStateOf(emptySet<Long>()) }
            CategoryIdListField(
                label = "IDs",
                selected = value,
                onSelectedChange = {
                    value = it
                    selected = it
                },
                modifier = Modifier.testTag("category-ids"),
            )
        }

        onNodeWithTag("category-ids").performTextInput("1,")
        onNodeWithTag("category-ids").performTextInput("2")

        selected shouldBe setOf(1L, 2L)
    }
}
