import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: 'src/components.d.ts',
      dirs: [],
      directives: true,
      resolvers: [ElementPlusResolver()]
    })
  ],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    host: '127.0.0.1',
    port: 18100,
    strictPort: true,
    proxy: {
      '/control': { target: 'http://127.0.0.1:18082', changeOrigin: false },
      '/actuator': { target: 'http://127.0.0.1:18082', changeOrigin: false },
      '/runtime-config': { target: 'http://127.0.0.1:18082', changeOrigin: false },
      '/mcp-local': { target: 'http://127.0.0.1:18083', changeOrigin: false },
      '^/integration/': { target: 'http://127.0.0.1:18081', changeOrigin: false }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    include: ['src/**/*.test.ts'],
    coverage: { reporter: ['text', 'html'] },
    server: { deps: { inline: ['element-plus'] } }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/element-plus')) return 'element-plus'
          if (id.includes('node_modules/@tanstack')) return 'tanstack-query'
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia')) return 'vue-runtime'
        }
      }
    }
  }
})
