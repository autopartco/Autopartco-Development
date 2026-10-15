import { ApiError, simulateLatency } from '@/lib/api'
import type { AddressService, PersonAddressDto, PersonAddressRequestDto } from '../types'

/**
 * Direcciones simuladas en memoria (se pierden al recargar). Arranca con dos:
 * Bogotá (predeterminada) y Medellín. `create` agrega y, si `isDefault`, relega a las demás.
 * `findMockAddress` lo usa el checkout simulado para responder 404 `ADDRESS_NOT_FOUND`.
 */
const addresses: PersonAddressDto[] = [
  {
    id: 'addr-bogota',
    label: 'Casa',
    isDefault: true,
    addressLine: 'Calle 10 # 20-30',
    addressComplement: 'Apto 401',
    city: 'Bogotá',
    department: 'Cundinamarca',
    country: 'Colombia',
    postalCode: '110111',
    latitude: null,
    longitude: null,
  },
  {
    id: 'addr-medellin',
    label: 'Taller',
    isDefault: false,
    addressLine: 'Carrera 43A # 1 Sur-50',
    addressComplement: null,
    city: 'Medellín',
    department: 'Antioquia',
    country: 'Colombia',
    postalCode: '050021',
    latitude: null,
    longitude: null,
  },
]

export function findMockAddress(id: string): PersonAddressDto | undefined {
  return addresses.find((address) => address.id === id)
}

export function createMockAddressService(): AddressService {
  return {
    simulated: true,

    async list() {
      await simulateLatency(350)
      return addresses.map((address) => ({ ...address }))
    },

    async create(input: PersonAddressRequestDto) {
      await simulateLatency(450)
      const { location } = input
      const fieldErrors: Record<string, string> = {}
      if (!input.label.trim()) fieldErrors.label = 'La etiqueta es obligatoria.'
      if (!location.addressLine.trim()) fieldErrors['location.addressLine'] = 'La dirección es obligatoria.'
      if (!location.city.trim()) fieldErrors['location.city'] = 'La ciudad es obligatoria.'
      if (!location.department.trim()) fieldErrors['location.department'] = 'El departamento es obligatorio.'
      if (Object.keys(fieldErrors).length > 0) {
        throw new ApiError('Los datos enviados no son válidos.', { status: 400, code: 'VALIDATION_ERROR', fieldErrors })
      }
      const created: PersonAddressDto = {
        id: `addr-${crypto.randomUUID()}`,
        label: input.label.trim(),
        isDefault: input.isDefault || addresses.length === 0,
        addressLine: location.addressLine.trim(),
        addressComplement: location.addressComplement?.trim() || null,
        city: location.city.trim(),
        department: location.department.trim(),
        country: location.country?.trim() || 'Colombia',
        postalCode: location.postalCode?.trim() || null,
        latitude: location.latitude ?? null,
        longitude: location.longitude ?? null,
      }
      if (created.isDefault) for (const address of addresses) address.isDefault = false
      addresses.push(created)
      return { ...created }
    },
  }
}
