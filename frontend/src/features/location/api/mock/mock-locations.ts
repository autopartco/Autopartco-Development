/**
 * Ciudades y sedes simuladas para desarrollar la UI de cercanía sin backend.
 * Barrios reales; las sedes y sus coordenadas son ficticias (aproximadas al barrio).
 */
import type { City, GeoPoint } from '../types'

export type MockCity = City

export interface MockBranch {
  cityId: string
  storeId: string
  branchName: string
  area: string
  point: GeoPoint
}

export const MOCK_CITIES: MockCity[] = [
  { id: 'bogota', name: 'Bogotá', department: 'Bogotá D.C.' },
  { id: 'medellin', name: 'Medellín', department: 'Antioquia' },
  { id: 'cali', name: 'Cali', department: 'Valle del Cauca' },
  { id: 'barranquilla', name: 'Barranquilla', department: 'Atlántico' },
  { id: 'bucaramanga', name: 'Bucaramanga', department: 'Santander' },
  { id: 'pereira', name: 'Pereira', department: 'Risaralda' },
]

export const MOCK_BRANCHES: MockBranch[] = [
  // Bogotá
  { cityId: 'bogota', storeId: 'store-barrio-colombia', branchName: 'Sede 7 de Agosto', area: '7 de Agosto', point: { lat: 4.6612, lng: -74.0705 } },
  { cityId: 'bogota', storeId: 'store-santander', branchName: 'Sede Galerías', area: 'Galerías', point: { lat: 4.6425, lng: -74.0772 } },
  { cityId: 'bogota', storeId: 'store-la13', branchName: 'Sede Calle 13', area: 'Puente Aranda', point: { lat: 4.6178, lng: -74.0958 } },
  { cityId: 'bogota', storeId: 'store-valle', branchName: 'Sede Las Ferias', area: 'Las Ferias', point: { lat: 4.6935, lng: -74.0846 } },
  { cityId: 'bogota', storeId: 'store-costa', branchName: 'Sede Restrepo', area: 'Restrepo', point: { lat: 4.5884, lng: -74.1027 } },

  // Medellín
  { cityId: 'medellin', storeId: 'store-barrio-colombia', branchName: 'Sede Barrio Colombia', area: 'Barrio Colombia', point: { lat: 6.2275, lng: -75.5745 } },
  { cityId: 'medellin', storeId: 'store-la13', branchName: 'Sede Guayabal', area: 'Guayabal', point: { lat: 6.203, lng: -75.59 } },
  { cityId: 'medellin', storeId: 'store-eje', branchName: 'Sede Belén', area: 'Belén', point: { lat: 6.231, lng: -75.597 } },
  { cityId: 'medellin', storeId: 'store-valle', branchName: 'Sede Centro', area: 'La Candelaria', point: { lat: 6.249, lng: -75.569 } },
  { cityId: 'medellin', storeId: 'store-santander', branchName: 'Sede Itagüí', area: 'Itagüí', point: { lat: 6.172, lng: -75.606 } },

  // Cali
  { cityId: 'cali', storeId: 'store-costa', branchName: 'Sede Alameda', area: 'Alameda', point: { lat: 3.438, lng: -76.535 } },
  { cityId: 'cali', storeId: 'store-valle', branchName: 'Sede San Nicolás', area: 'San Nicolás', point: { lat: 3.452, lng: -76.526 } },
  { cityId: 'cali', storeId: 'store-la13', branchName: 'Sede Versalles', area: 'Versalles', point: { lat: 3.463, lng: -76.528 } },
  { cityId: 'cali', storeId: 'store-eje', branchName: 'Sede El Ingenio', area: 'El Ingenio', point: { lat: 3.387, lng: -76.528 } },

  // Barranquilla
  { cityId: 'barranquilla', storeId: 'store-valle', branchName: 'Sede Boston', area: 'Boston', point: { lat: 11.005, lng: -74.81 } },
  { cityId: 'barranquilla', storeId: 'store-santander', branchName: 'Sede Riomar', area: 'Riomar', point: { lat: 11.018, lng: -74.825 } },
  { cityId: 'barranquilla', storeId: 'store-costa', branchName: 'Sede Centro', area: 'Centro', point: { lat: 10.984, lng: -74.781 } },
  { cityId: 'barranquilla', storeId: 'store-barrio-colombia', branchName: 'Sede Murillo', area: 'Murillo', point: { lat: 10.96, lng: -74.8 } },

  // Bucaramanga
  { cityId: 'bucaramanga', storeId: 'store-santander', branchName: 'Sede Cabecera', area: 'Cabecera', point: { lat: 7.1205, lng: -73.1075 } },
  { cityId: 'bucaramanga', storeId: 'store-la13', branchName: 'Sede San Francisco', area: 'San Francisco', point: { lat: 7.126, lng: -73.121 } },
  { cityId: 'bucaramanga', storeId: 'store-costa', branchName: 'Sede Centro', area: 'Centro', point: { lat: 7.119, lng: -73.127 } },
  { cityId: 'bucaramanga', storeId: 'store-eje', branchName: 'Sede Provenza', area: 'Provenza', point: { lat: 7.087, lng: -73.115 } },

  // Pereira
  { cityId: 'pereira', storeId: 'store-eje', branchName: 'Sede Circunvalar', area: 'Circunvalar', point: { lat: 4.8115, lng: -75.6885 } },
  { cityId: 'pereira', storeId: 'store-la13', branchName: 'Sede Centro', area: 'Centro', point: { lat: 4.814, lng: -75.6965 } },
  { cityId: 'pereira', storeId: 'store-valle', branchName: 'Sede Dosquebradas', area: 'Dosquebradas', point: { lat: 4.835, lng: -75.675 } },
  { cityId: 'pereira', storeId: 'store-barrio-colombia', branchName: 'Sede Cuba', area: 'Cuba', point: { lat: 4.796, lng: -75.735 } },
]
