<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps<{
  code: string
  language?: string
  readOnly?: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)
const editor = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null)

onMounted(() => {
  if (!containerRef.value) return

  editor.value = monaco.editor.create(containerRef.value, {
    value: props.code,
    language: props.language || 'java',
    theme: 'vs',
    readOnly: props.readOnly ?? true,
    automaticLayout: true,
    minimap: { enabled: true },
    fontSize: 14,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    folding: true,
    wordWrap: 'on',
  })
})

watch(() => props.code, (newCode) => {
  if (editor.value) {
    editor.value.setValue(newCode)
    // 更新语言
    const model = editor.value.getModel()
    if (model && props.language) {
      monaco.editor.setModelLanguage(model, props.language)
    }
  }
})

watch(() => props.language, (newLang) => {
  if (editor.value && newLang) {
    const model = editor.value.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, newLang)
    }
  }
})

onBeforeUnmount(() => {
  editor.value?.dispose()
})
</script>

<template>
  <div ref="containerRef" style="width: 100%; height: 100%; min-height: 400px;"></div>
</template>
