import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin, type VueQueryPluginOptions } from '@tanstack/vue-query'
import './styles/index.css'
import App from './App.vue'
import { router } from './router'

const queryOptions: VueQueryPluginOptions = {
  queryClientConfig: {
    defaultOptions: {
      queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false }
    }
  }
}

createApp(App)
  .use(createPinia())
  .use(router)
  .use(VueQueryPlugin, queryOptions)
  .mount('#app')
