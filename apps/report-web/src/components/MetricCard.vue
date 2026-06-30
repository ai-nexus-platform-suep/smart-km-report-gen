<template>
  <div class="metric-card interactive-lift">
    <div>
      <span class="terminal-label">{{ code }}</span>
      <strong>{{ value }}</strong>
    </div>
    <p>{{ label }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  code: string;
  value: string | number;
  label: string;
}>();
</script>

<style scoped>
.metric-card {
  display: grid;
  gap: 12px;
  min-height: 112px;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-md);
  color: var(--text-inverse);
  background:
    linear-gradient(135deg, rgba(0, 184, 217, 0.16), transparent 46%),
    var(--bg-terminal);
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-family: var(--font-display);
  font-size: 38px;
  line-height: 1;
}

.metric-card p {
  margin: 0;
  color: rgba(248, 251, 255, 0.72);
}
</style>
