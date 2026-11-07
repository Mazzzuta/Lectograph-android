package ru.lectograph.app.transcription

import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

class WhisperJniTest {
    @Test fun kotlinKeepsNativeEntryPointsOnTheOriginalClass() {
        val type = Class.forName("ru.lectograph.app.transcription.WhisperEngine", false, javaClass.classLoader)
        val listener = Class.forName("ru.lectograph.app.transcription.WhisperEngine\$Listener", false, javaClass.classLoader)
        val load = type.getDeclaredMethod("load", String::class.java)
        assertTrue(Modifier.isNative(load.modifiers)); assertEquals(Long::class.javaPrimitiveType, load.returnType)
        for (name in listOf("free", "cancelNative")) assertTrue(Modifier.isNative(type.getDeclaredMethod(name, Long::class.javaPrimitiveType).modifiers))
        assertTrue(Modifier.isNative(type.getDeclaredMethod("decode", Long::class.javaPrimitiveType, FloatArray::class.java, listener).modifiers))
        assertEquals(Void.TYPE, listener.getMethod("onSegment", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, ByteArray::class.java).returnType)
    }
}
