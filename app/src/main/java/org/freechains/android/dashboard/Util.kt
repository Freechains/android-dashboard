package org.freechains.android.dashboard

import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import org.freechains.host.hashSplit
import org.freechains.host.toHeight

const val LEN1000_pay = 1000
const val LEN10_shared = 10
const val LEN20_pubpbt = 1  // TODO 20

fun String.block2out () : String {
    //println(">>> " + this)
    val (height,hash) = this.hashSplit()
    return height.toString() + "_" + hash.take(3) + "..." + hash.takeLast(3)
}

fun String.pub2out () : String {
    return this.take(3) + "..." + this.takeLast(3)
}

fun String.chain2out () : String {
    return when {
        this.startsWith('@') -> {
            val n = if (this.startsWith("@!")) 2 else 1
            val pub = this.drop(n)
            this.take(n) + pub.pub2out()
        }
        else -> this
    }
}

fun makeLinkClickable (strBuilder: SpannableStringBuilder, span: URLSpan, cb: (String)->Unit) {
    val start: Int = strBuilder.getSpanStart(span)
    val end: Int = strBuilder.getSpanEnd(span)
    val flags: Int = strBuilder.getSpanFlags(span)
    val clickable: ClickableSpan = object : ClickableSpan() {
        override fun onClick(view: View) {
            cb(span.url)
        }
    }
    strBuilder.setSpan(clickable, start, end, flags)
    strBuilder.removeSpan(span)
}

fun TextView.setTextViewHTML (html: String, cb: (String)->Unit) {
    val sequence: CharSequence = Html.fromHtml(html)
    val strBuilder = SpannableStringBuilder(sequence)
    val urls = strBuilder.getSpans(0, sequence.length, URLSpan::class.java)
    for (span in urls) {
        makeLinkClickable(strBuilder, span!!, cb)
    }
    this.text = strBuilder
    this.movementMethod = LinkMovementMethod.getInstance()
}
