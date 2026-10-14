import { apiRequest } from '@/lib/api'
import type { AddressService, PersonAddressDto, PersonAddressRequestDto } from '../types'
import { requireAccessToken } from './http-checkout-service'

/** Direcciones del comprador (`core/identity/web/MeController`: `GET` y `POST /me/addresses`). */
export function createHttpAddressService(): AddressService {
  return {
    simulated: false,
    async list() {
      return apiRequest<PersonAddressDto[]>('/me/addresses', { token: await requireAccessToken() })
    },
    async create(input: PersonAddressRequestDto) {
      return apiRequest<PersonAddressDto>('/me/addresses', { method: 'POST', body: input, token: await requireAccessToken() })
    },
  }
}
