package gm.tieba.tabswitch.hooker.deobfuscation

import org.luckypray.dexkit.query.matchers.ClassMatcher

abstract class Matcher(private val name: String) {
    var classMatcher: ClassMatcher? = null
    var reqVersion: String? = null

    override fun toString(): String = name

    fun setBaseClassMatcher(baseClassMatcher: ClassMatcher) : Matcher {
        classMatcher = baseClassMatcher
        return this
    }

    fun setRequiredVersion(version: String) : Matcher {
        reqVersion= version
        return this
    }
}

class StringMatcher @JvmOverloads constructor(val str: String, val name: String = str) : Matcher(name)

class SmaliMatcher @JvmOverloads constructor(val descriptor: String, val name: String = descriptor) : Matcher(name)

class MethodNameMatcher(val methodName: String, val name: String) : Matcher(name)

class ReturnTypeMatcher<T>(val returnType: Class<T>, val name: String) : Matcher(name)

class ResMatcher(val id: Long, val name: String) : Matcher(name)