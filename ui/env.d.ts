/// <reference types="@rsbuild/core/types" />
/// <reference types="unplugin-icons/types/vue" />

declare namespace NodeJS {
  interface ProcessEnv {
    /** 构建时从 gradle.properties 注入，用于 Console / UC 的 Runtime 缓存版本参数。 */
    PLUGIN_VERSION: string
  }
}
