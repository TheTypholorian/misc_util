package net.typho.misc_util

object KtServiceLoader {
    const val PREFIX = "META-INF/services/"
    
    interface Provider<S : Any> {
        val type: Class<out S>

        fun get(): S
    }

    @JvmStatic
    fun <S : Any> List<Provider<S>>.loadAll() = map { it.get() }

    @JvmOverloads
    @JvmStatic
    fun <S : Any> load(service: Class<S>, initStatic: Boolean = false, loader: ClassLoader = Thread.currentThread().contextClassLoader): List<Provider<S>> {
        val configs = loader.getResources(PREFIX + service.name).toList()
        val impls = configs.flatMapTo(mutableSetOf()) {
            val connection = it.openConnection()
            connection.useCaches = false
            connection.getInputStream().reader().readLines().filter { it.isNotBlank() }
        }
        return load(service, impls, initStatic, loader)
    }

    @JvmOverloads
    @JvmStatic
    fun <S : Any> load(service: Class<S>, impls: Collection<String>, initStatic: Boolean = false, loader: ClassLoader = Thread.currentThread().contextClassLoader): List<Provider<S>> {
        return impls.map { impl ->
            val cls = Class.forName(impl, initStatic, loader)

            if (!service.isAssignableFrom(cls)) {
                throw IllegalStateException("Service class ${cls.name} does not extend ${service.name}")
            }

            @Suppress("UNCHECKED_CAST")
            cls as Class<out S>

            cls.kotlin.objectInstance?.let { obj ->
                return@map object : Provider<S> {
                    override val type: Class<out S>
                        get() = cls

                    override fun get() = obj
                }
            }

            return@map object : Provider<S> {
                override val type: Class<out S>
                    get() = cls
                val ctor by lazy {
                    cls.getConstructor().also { it.isAccessible = true }
                }

                override fun get() = ctor.newInstance()
            }
        }
    }
}