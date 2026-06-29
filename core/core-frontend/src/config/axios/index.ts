import { service } from './service'

import { config } from './config'

const { default_headers } = config

const request = (option: any) => {
  const { url, method, params, data, headersType, responseType, loading } = option

  let fullName = ''
  try {
    const userStr = localStorage.getItem('yee-user')
    if (userStr) {
      const userObj = JSON.parse(userStr)
      if (userObj && userObj.fullName) {
        fullName = userObj.fullName
      }
    }
  } catch (error) {
    console.warn('解析 yee-user 失败', error)
  }

  return service({
    url: url,
    method,
    loading,
    params,
    data,
    responseType: responseType,
    headers: {
      'Content-Type': headersType || default_headers,
      ...(fullName ? { 'Full-Name': encodeURIComponent(fullName) } : {})
    }
  })
}

export default {
  get: <T = any>(option: any) => {
    return request({ method: 'get', ...option }) as unknown as T
  },
  post: <T = any>(option: any) => {
    return request({ method: 'post', ...option }) as unknown as T
  },
  delete: <T = any>(option: any) => {
    return request({ method: 'delete', ...option }) as unknown as T
  },
  put: <T = any>(option: any) => {
    return request({ method: 'put', ...option }) as unknown as T
  }
}
