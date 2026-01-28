import request from '@/utils/request'

export function listTraces(params) {
    return request({
        url: '/api/observability/traces',
        method: 'get',
        params
    })
}

export function getTrace(id) {
    return request({
        url: `/api/observability/traces/${id}`,
        method: 'get'

    })
}

export function deleteTrace(id) {
    return request({
        url: `/api/observability/traces/${id}`,
        method: 'delete'
    })
}

export function getModels() {
    return request({
        url: '/api/observability/models',
        method: 'get'
    })
}

export function getProviders() {
    return request({
        url: '/api/observability/providers',
        method: 'get'
    })
}

export function getTraceSources() {
    return request({
        url: '/api/observability/trace-sources',
        method: 'get'
    })
}
