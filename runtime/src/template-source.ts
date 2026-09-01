// 自定义模板来源：后台配置（接口）> 主题 <template>（DOM）> 内置默认
import type { HipComponent } from './hip-data'
import type { TemplateSource } from './template-engine'
import { API_BASE } from './identity'

/**
 * 超时上限。
 *
 * <p>延迟注册期间 `hip-*` 标签露出的是插槽内容（用户卡是触发元素，行内组件通常为空），
 * 这段时间直接体现为首屏内容缺失——所以请求在入口顶层立即发起（与 script 解析同时，
 * 不等任何组件 connect），超时也压到 1 秒。3 秒的话最坏情况首屏空白 3 秒，
 * 比不做自定义还差。
 */
const FETCH_TIMEOUT_MS = 1000

interface TemplateItem {
  component: string
  html: string
  css: string
}

/**
 * 拉取已启用的后台自定义模板。
 *
 * <p>失败或超时一律返回空表 = 全部走下一级来源，静默降级。
 * 这与模板内容出错（红字不回落）是两回事：此刻只是还没拿到模板，
 * 若也不回落，接口一抖动全站身份行就整个消失。
 *
 * <p>响应里出现 = 已启用且有内容（接口层已过滤空模板），这里无需再判空。
 */
export function fetchTemplates(): Promise<Map<string, TemplateSource>> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS)
  return fetch(`${API_BASE}/custom-templates`, {
    headers: { Accept: 'application/json' },
    signal: controller.signal,
  })
    .then((response) => (response.ok ? (response.json() as Promise<{ items?: TemplateItem[] }>) : null))
    .catch(() => null)
    .then((body) => {
      const map = new Map<string, TemplateSource>()
      for (const item of body?.items || []) {
        if (item?.component && item.html) {
          map.set(item.component, { html: item.html, css: item.css || '' })
        }
      }
      return map
    })
    .finally(() => clearTimeout(timer))
}

// ── 模板注册表 ────────────────────────────────────────
// 启动时写入一次，之后只读。模板改动需刷新页面生效（接口是协商缓存，刷新即拿到新的）。

let registry: Map<string, TemplateSource> = new Map()

/** 启动时写入后台拉取结果。 */
export function setTemplates(templates: Map<string, TemplateSource>): void {
  registry = templates
}

/**
 * 主题内嵌模板的查询结果缓存（含「查过但没有」，故值可为 null）。
 *
 * <p>首次取用时才查 DOM，不在 `define` 时查：这样只要 `<template>` 出现在使用它的
 * 组件之前就有效，比「必须早于 runtime 脚本」宽松。文档仍建议放进 `<head>`。
 */
const themeCache = new Map<string, TemplateSource | null>()

/**
 * 读主题内嵌的 `<template data-hip="identity">`。
 *
 * <p>整段内容当作 HTML 框（里面可以有 `<style>` 和 `<script>`），CSS 框留空 ——
 * 主题作者在一个标签里交付全部，不必分两处。
 */
function readThemeTemplate(component: string): TemplateSource | null {
  const element = document.querySelector<HTMLTemplateElement>(
    `template[data-hip="${component}"]`,
  )
  const html = element?.innerHTML.trim()
  return html ? { html, css: '' } : null
}

/**
 * 取某组件的模板：后台配置 > 主题 `<template>`；两者都没有时返回 undefined
 * （调用方回落内置默认）。按组件独立判断，可以只自定义身份行。
 */
export function getTemplate(component: HipComponent): TemplateSource | undefined {
  const configured = registry.get(component)
  if (configured) {
    return configured
  }
  if (!themeCache.has(component)) {
    themeCache.set(component, readThemeTemplate(component))
  }
  return themeCache.get(component) || undefined
}
