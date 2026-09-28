package net.typho.misc_util

import java.util.ServiceLoader

object KtServiceLoader {
    const val PREFIX = "META-INF/services/"

    @JvmStatic
    fun <S : Any> List<ServiceLoader.Provider<S>>.loadAll() = map { it.get() }

    @JvmOverloads
    @JvmStatic
    fun <S : Any> load(service: Class<S>, loader: ClassLoader = Thread.currentThread().contextClassLoader): List<ServiceLoader.Provider<S>> {
        val configs = loader.getResources(PREFIX + service.name).toList()
        val impls = configs.flatMapTo(mutableSetOf()) {
            val connection = it.openConnection()
            connection.useCaches = false
            connection.getInputStream().reader().readAllLines().filter { it.isNotBlank() }
        }
        return load(service, impls, loader)
    }

    @JvmOverloads
    @JvmStatic
    fun <S : Any> load(service: Class<S>, impls: Collection<String>, loader: ClassLoader = Thread.currentThread().contextClassLoader): List<ServiceLoader.Provider<S>> {
        return impls.map {
            val cls = Class.forName(it, false, loader)

            if (!service.isAssignableFrom(cls)) {
                throw IllegalStateException("Service class ${cls.name} does not extend ${service.name}")
            }

            @Suppress("UNCHECKED_CAST")
            cls as Class<out S>

            cls.kotlin.objectInstance?.let { obj ->
                return@map object : ServiceLoader.Provider<S> {
                    override fun type() = cls

                    override fun get() = obj
                }
            }

            val ctor = cls.getConstructor()
            ctor.isAccessible = true

            return@map object : ServiceLoader.Provider<S> {
                override fun type() = cls

                override fun get() = ctor.newInstance()
            }
        }
    }
}