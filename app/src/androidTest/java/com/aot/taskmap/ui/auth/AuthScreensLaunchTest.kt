package com.aot.taskmap.ui.auth

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aot.taskmap.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreensLaunchTest {

    @Test
    fun loginActivity_launches() {
        ActivityScenario.launch(LoginActivity::class.java)
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))
    }
}
