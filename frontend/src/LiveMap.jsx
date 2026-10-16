import { useEffect } from 'react'
import { MapContainer, Marker, Polyline, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import './live-map.css'

const pin = (color, symbol) => L.divIcon({
  className: 'cityride-map-pin',
  html: `<span style="--pin-color:${color}">${symbol}</span>`,
  iconSize: [32, 32],
  iconAnchor: [16, 16],
})

const pickupIcon = pin('#1d7957', 'P')
const destinationIcon = pin('#dc7652', 'D')
const driverIcon = pin('#3655d8', '🚘')

function FitRoute({ positions }) {
  const map = useMap()
  const key = positions.map(([lat, lng]) => `${lat},${lng}`).join(';')
  useEffect(() => {
    if (positions.length > 1) map.fitBounds(positions, { padding: [32, 32], maxZoom: 15 })
    else if (positions.length === 1) map.setView(positions[0], 14)
  }, [map, key])
  return null
}

export default function LiveMap({ route, driverLocation, className = '' }) {
  const positions = route?.geometry ?? []
  const pickup = route?.pickupLatitude != null ? [route.pickupLatitude, route.pickupLongitude] : positions[0]
  const destination = route?.destinationLatitude != null
    ? [route.destinationLatitude, route.destinationLongitude]
    : positions.at(-1)
  const center = pickup ?? [22.9734, 78.6569]

  return (
    <div className={`cityride-live-map ${className}`}>
      <MapContainer center={center} zoom={pickup ? 12 : 5} scrollWheelZoom className="cityride-map">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {positions.length > 1 && <Polyline positions={positions} pathOptions={{ color: '#248263', weight: 5 }} />}
        {pickup && <Marker position={pickup} icon={pickupIcon} />}
        {destination && <Marker position={destination} icon={destinationIcon} />}
        {driverLocation && <Marker position={driverLocation} icon={driverIcon} />}
        <FitRoute positions={positions.length ? positions : [center]} />
      </MapContainer>
      <small className="map-attribution">Map data &copy; OpenStreetMap contributors · Routing by OSRM</small>
    </div>
  )
}
