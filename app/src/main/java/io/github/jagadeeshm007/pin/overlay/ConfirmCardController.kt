package io.github.jagadeeshm007.pin.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.MainThread
import io.github.jagadeeshm007.pin.R
import io.github.jagadeeshm007.pin.databinding.OverlayConfirmCardBinding
import io.github.jagadeeshm007.pin.notification.ConfirmationBroker
import io.github.jagadeeshm007.pin.notification.PendingConfirmation
import io.github.jagadeeshm007.pin.notification.TaskControl

/**
 * Shows the pending confirmation as a card that stays until it is answered, stopped or
 * timed out (spec PIN-13 AC-7). System pop-ups close after a few seconds, so the
 * question lives in Pin's own overlay. The card's window is touchable but not modal:
 * touches outside the card reach the app underneath.
 */
class ConfirmCardController(private val service: AccessibilityService, private val broker: ConfirmationBroker) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var binding: OverlayConfirmCardBinding? = null
    private var shownToken: Long? = null

    /** Shows [question], or removes the card when null. Call on the main thread. */
    @MainThread
    fun render(question: PendingConfirmation?) {
        if (question == null) {
            remove()
            return
        }
        val card = binding ?: create()
        card.confirmQuestion.text = question.reason
        shownToken = question.token
        card.confirmYes.setOnClickListener { answer(question.token, yes = true) }
        card.confirmNo.setOnClickListener { answer(question.token, yes = false) }
        card.confirmStop.setOnClickListener { TaskControl.requestStop() }
    }

    /** Removes the card window. */
    @MainThread
    fun remove() {
        binding?.let { windowManager.removeViewImmediate(it.root) }
        binding = null
        shownToken = null
    }

    private fun answer(token: Long, yes: Boolean) {
        if (shownToken == token) broker.answer(token, yes)
    }

    private fun create(): OverlayConfirmCardBinding {
        val themed = ContextThemeWrapper(service, R.style.Theme_Pin_Overlay)
        val card = OverlayConfirmCardBinding.inflate(LayoutInflater.from(themed))
        windowManager.addView(card.root, layoutParams())
        binding = card
        return card
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP
        y = service.resources.getDimensionPixelSize(R.dimen.confirm_card_top)
        title = "Pin confirmation"
    }
}
