import { createApp } from 'vue'
import '@/style/index.less'
import 'normalize.css/normalize.css'
import '@antv/s2/dist/style.min.css'
import 'vxe-table/lib/style.css'
import App from './App.vue'
import { setupI18n } from '@/plugins/vue-i18n'
import { setupStore } from '@/store'
import { setupRouter } from '@/router'
import { setupElementPlus, setupElementPlusIcons } from '@/plugins/element-plus'
// 注册数据大屏组件
import { setupCustomComponent } from '@/custom-component'
import { installDirective } from '@/directive'
import '@/utils/DateUtil'
import '@/permission'
import WebSocketPlugin from '../../websocket'
import { loadScript } from '@/utils/RemoteJs'
const setupAll = async () => {
  const app = createApp(App)
  installDirective(app)
  setupStore(app)
  await setupI18n(app)
  setupRouter(app)
  setupElementPlus(app)
  setupCustomComponent(app)
  setupElementPlusIcons(app)
  app.use(WebSocketPlugin)
  app.mount('#app')
}

setupAll()
try {
  const customScriptUrl =
    localStorage.getItem('DE_CUSTOM_SCRIPT_URL') ||
    window.location.origin + '/data/custom-script.js'
  if (customScriptUrl) {
    loadScript(customScriptUrl, 'de-custom-script')
    console.log(`自定义脚本加载成功: ${customScriptUrl}`)
  } else {
    console.warn('未在 localStorage 中找到 DE_CUSTOM_SCRIPT_URL，跳过自定义脚本加载')
  }
} catch (e) {
  console.error('自定义脚本加载失败', e)
}
