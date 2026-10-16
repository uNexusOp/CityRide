const NOMINATIM = 'https://nominatim.openstreetmap.org/search'
const OSRM = 'https://router.project-osrm.org/route/v1/driving'

async function geocode(place) {
  const coordinates = place.match(/(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)/)
  if (coordinates) {
    const latitude = Number(coordinates[1])
    const longitude = Number(coordinates[2])
    if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
      return { latitude, longitude, label: place.trim() }
    }
  }
  const response = await fetch(
    `${NOMINATIM}?format=jsonv2&limit=1&countrycodes=in&q=${encodeURIComponent(place)}`,
    {
      headers: { 'Accept-Language': 'en-IN' },
    },
  )
  if (!response.ok) throw new Error('Location search is temporarily unavailable. Try again shortly.')
  const results = await response.json()
  if (!results.length) throw new Error(`Could not find “${place}” in India. Try adding an Indian city or PIN code.`)
  return {
    latitude: Number(results[0].lat),
    longitude: Number(results[0].lon),
    label: results[0].display_name,
  }
}

export async function planRoute(pickup, destination) {
  const [from, to] = await Promise.all([geocode(pickup), geocode(destination)])
  const response = await fetch(
    `${OSRM}/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson`,
  )
  if (!response.ok) throw new Error('Route planning is temporarily unavailable. Try again shortly.')
  const result = await response.json()
  const route = result.routes?.[0]
  if (!route) throw new Error('No driving route could be found between these locations.')
  const distanceKm = Math.round((route.distance / 1000) * 100) / 100
  if (distanceKm < 0.5 || distanceKm > 500) {
    throw new Error('This route must be between 0.5 and 500 km.')
  }
  return {
    pickup: from.label,
    destination: to.label,
    pickupLatitude: from.latitude,
    pickupLongitude: from.longitude,
    destinationLatitude: to.latitude,
    destinationLongitude: to.longitude,
    distanceKm,
    durationMinutes: Math.max(1, Math.ceil(route.duration / 60)),
    geometry: route.geometry.coordinates.map(([longitude, latitude]) => [latitude, longitude]),
  }
}
