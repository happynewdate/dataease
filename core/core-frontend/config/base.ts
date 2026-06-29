import pkg from '../package.json'
import viteCompression from 'vite-plugin-compression'

type VendorChunkMatcher = string | { packagePrefix: string }

const vendorChunkGroups: Record<string, VendorChunkMatcher[]> = {
  runtime: ['@babel', 'babel-runtime', 'tslib'],
  vue: [
    'vue',
    'vue-router',
    'vue-router_2',
    'pinia',
    'vue-i18n',
    'vue-demi',
    '@vue',
    '@vueuse',
    '@intlify',
    'mitt',
    'vue-types'
  ],
  'element-plus': [
    'element-plus-secondary',
    '@element-plus',
    '@popperjs',
    '@ctrl',
    'async-validator',
    'memoize-one',
    'normalize-wheel-es'
  ],
  table: ['vxe-table', '@antv/s2'],
  charts: [
    'echarts',
    'zrender',
    '@antv',
    '@amap',
    { packagePrefix: 'd3-' },
    '@turf',
    '@mapbox',
    'mapbox-gl',
    'hammerjs',
    'regl',
    'gl-matrix',
    'geojson-vt',
    'supercluster',
    'topojson-client',
    'earcut',
    'pbf',
    'viewport-mercator-project',
    'polygon-clipping',
    'robust-predicates',
    'splaytree',
    'fmin',
    'fecha',
    'pdfast'
  ],
  editor: [
    'tinymce',
    '@tinymce',
    '@npkg',
    'ace-builds',
    '@codemirror',
    'vue-codemirror',
    'vue3-ace-editor'
  ],
  export: ['jspdf', 'html2canvas', 'html-to-image', 'file-saver', 'exceljs'],
  media: ['video.js', '@videojs-player', 'flv.js'],
  lodash: ['lodash', 'lodash-es']
}

const getPackageName = (id: string) => {
  const [, dependencyPath] = id.split('node_modules/')
  if (!dependencyPath) {
    return ''
  }

  const parts = dependencyPath.split('/')
  return dependencyPath.startsWith('@') ? `${parts[0]}/${parts[1]}` : parts[0]
}

const matchVendorPackage = (matcher: VendorChunkMatcher, packageName: string) => {
  if (typeof matcher !== 'string') {
    return packageName.startsWith(matcher.packagePrefix)
  }

  return (
    packageName === matcher || (matcher.startsWith('@') && packageName.startsWith(`${matcher}/`))
  )
}

const manualChunks = (id: string) => {
  if (id.includes('commonjsHelpers')) {
    return 'runtime'
  }

  if (!id.includes('node_modules')) {
    return
  }

  const packageName = getPackageName(id)
  const matchedGroup = Object.entries(vendorChunkGroups).find(([, packages]) =>
    packages.some(matcher => matchVendorPackage(matcher, packageName))
  )

  return matchedGroup?.[0]
}

export default {
  plugins: [
    viteCompression({
      // gzip静态资源压缩配置
      verbose: true, // 是否在控制台输出压缩结果
      disable: false, // 是否禁用压缩
      threshold: 10240, // 启用压缩的文件大小限制
      algorithm: 'gzip', // 采用的压缩算法
      ext: '.gz' // 生成的压缩包后缀
    })
  ],
  build: {
    rollupOptions: {
      output: {
        // 用于命名代码拆分时创建的共享块的输出命名
        chunkFileNames: `assets/chunk/[name]-${pkg.version}-${pkg.name}.js`,
        assetFileNames: `assets/[ext]/[name]-${pkg.version}-${pkg.name}.[ext]`,
        entryFileNames: `js/[name]-${pkg.version}-${pkg.name}.js`,
        manualChunks
      }
    },
    modulePreload: {
      resolveDependencies() {
        return []
      }
    },
    sourcemap: false
  }
}
