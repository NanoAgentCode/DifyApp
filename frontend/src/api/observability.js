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
