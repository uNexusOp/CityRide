import React, { useCallback, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Client } from '@stomp/stompjs'
import {
  ArrowDown, ArrowDownLeft, ArrowRight, ArrowUpRight, BadgeCheck, Banknote,
  CarFront, Check, ChevronDown, CircleHelp, Clock3, CreditCard, Crosshair, Download,
  LocateFixed, LogOut, MapPin, Menu, Navigation, ShieldCheck, Sparkles, Star,
  Wallet, X,
} from 'lucide-react'
import { api } from './api'
import LiveMap from './LiveMap'
import { planRoute } from './maps'
import './styles.css'
import './dialog.css'

const money = (amount) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: 2,
}).format(Number(amount ?? 0))
const dateLabel = (value) => new Intl.DateTimeFormat('en-IN', {
  month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
}).format(new Date(value))

function App() {
  const [session, setSession] = useState(() => {
    try { return JSON.parse(localStorage.getItem('cityride-session')) } catch { return null }
  })
  const [rides, setRides] = useState([])
  const [available, setAvailable] = useState([])
  const [adminSummary, setAdminSummary] = useState(null)
  const [adminRides, setAdminRides] = useState([])
  const [driverRankings, setDriverRankings] = useState([])
  const [supportTickets, setSupportTickets] = useState([])
  const [promoCodes, setPromoCodes] = useState([])
  const [view, setView] = useState('home')
  const [authMode, setAuthMode] = useState('login')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const [mobileNav, setMobileNav] = useState(false)
  const [dialog, setDialog] = useState(null)
  const [liveLocations, setLiveLocations] = useState({})

  const showError = (error) => setNotice(error.message || 'Something went wrong. Please try again.')
  const refresh = useCallback(async () => {
    if (!session?.token) return
    try {
      if (session.user.role === 'DRIVER') {
        const [myRides, requests, rankings] = await Promise.all([
          api.mine(session.token), api.available(session.token), api.driverRankings(session.token),
        ])
        setRides(myRides)
        setAvailable(requests)
        setDriverRankings(rankings)
      } else if (session.user.role !== 'ADMIN') {
        setRides(await api.mine(session.token))
      }
      if (session.user.role === 'ADMIN') {
        const [summary, allRides, rankings, tickets, promos] = await Promise.all([
          api.adminSummary(session.token),
          api.adminRides(session.token),
          api.driverRankings(session.token),
          api.supportTickets(session.token),
          api.promoCodes(session.token),
        ])
        setAdminSummary(summary)
        setAdminRides(allRides)
        setDriverRankings(rankings)
        setSupportTickets(tickets)
        setPromoCodes(promos)
      }
    } catch (error) {
      if (/401|403/.test(error.message)) signOut()
      else showError(error)
    }
  }, [session])

  useEffect(() => {
    if (!session?.token) return
    refresh()
    const timer = window.setInterval(refresh, 5000)
    return () => window.clearInterval(timer)
  }, [session, refresh])

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1))
    const oauthToken = params.get('oauth_token')
    const paymentStatus = params.get('payment')
    if (!oauthToken && !paymentStatus) return
    window.history.replaceState({}, '', `${window.location.pathname}${window.location.search}`)
    if (paymentStatus === 'success') setNotice('Payment submitted securely. Your receipt will appear after Stripe confirms it.')
    if (paymentStatus === 'cancelled') setNotice('Checkout was cancelled. You can try again from your trip history.')
    if (!oauthToken) return
    api.me(oauthToken).then((user) => {
      localStorage.setItem('cityride-session', JSON.stringify({ token: oauthToken, user }))
      setSession({ token: oauthToken, user })
      setNotice('')
    }).catch((error) => setNotice(error.message || 'Google sign-in could not be completed.'))
  }, [])

  useEffect(() => {
    if (!session?.token) return undefined
    let watcher = null
    const client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${session.token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        if (session.user.role === 'DRIVER') {
          client.subscribe('/topic/drivers/requests', () => refresh())
        }
        if (session.user.role === 'ADMIN') {
          client.subscribe('/topic/admin/sos', (message) => {
            const alert = JSON.parse(message.body)
            setNotice(`SOS alert — trip ${alert.rideId}: ${alert.riderName}, ${alert.pickup} to ${alert.destination}`)
            refresh()
          })
        }
        client.subscribe('/user/queue/ride-events', (message) => {
          const event = JSON.parse(message.body)
          if (event.type === 'DRIVER_LOCATION') {
            setLiveLocations((locations) => ({
              ...locations, [event.rideId]: [event.latitude, event.longitude],
            }))
            return
          }
          if (event.type === 'DRIVER_ARRIVED') {
            setNotice(`Driver has arrived for trip ${event.rideId}.`)
          }
          if (event.sosTriggered) {
            setNotice(`An SOS alert was raised for trip ${event.rideId}.`)
          }
          if (event.status === 'ARRIVED') {
            setNotice(`Driver has arrived for trip ${event.rideId}.`)
          }
          refresh()
        })
        if (session.user.role === 'DRIVER' && session.user.available !== false && navigator.geolocation) {
          watcher = navigator.geolocation.watchPosition(({ coords }) => {
            if (client.connected) {
              client.publish({
                destination: '/app/driver/location',
                body: JSON.stringify({ latitude: coords.latitude, longitude: coords.longitude }),
              })
            }
          }, (error) => showError(new Error(`Live driver location is unavailable: ${error.message}`)), {
            enableHighAccuracy: true, maximumAge: 5000, timeout: 10000,
          })
        }
      },
      onStompError: (frame) => showError(new Error(frame.headers.message || 'Live connection failed.')),
    })
    client.activate()
    return () => {
      if (watcher !== null) navigator.geolocation.clearWatch(watcher)
      void client.deactivate()
    }
  }, [session, refresh])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(''), 4000)
    return () => window.clearTimeout(timer)
  }, [notice])

  const saveSession = (next) => {
    localStorage.setItem('cityride-session', JSON.stringify(next))
    setSession(next)
    setView('home')
    setNotice('')
  }
  const signOut = () => {
    localStorage.removeItem('cityride-session')
    setSession(null)
    setRides([])
    setAvailable([])
    setAdminSummary(null)
    setAdminRides([])
    setView('home')
  }

  const act = async (operation, successMessage) => {
    setLoading(true)
    try {
      await operation()
      await refresh()
      if (successMessage) setNotice(successMessage)
      setDialog(null)
      return true
    } catch (error) {
      showError(error)
      return false
    } finally { setLoading(false) }
  }

  const navItems = [
    { id: 'home', label: session?.user.role === 'DRIVER' ? 'Overview' : 'Book a ride', icon: Navigation },
    ...(session?.user.role !== 'ADMIN' ? [{ id: 'trips', label: 'Your trips', icon: Clock3 }] : []),
    ...(session?.user.role === 'DRIVER' ? [{ id: 'earnings', label: 'Earnings', icon: Wallet }] : []),
    ...(session?.user.role === 'ADMIN' ? [{ id: 'admin', label: 'System overview', icon: ShieldCheck }] : []),
    { id: 'help', label: 'Help center', icon: CircleHelp },
  ]

  if (!session) {
    return <AuthScreen mode={authMode} setMode={setAuthMode} onSuccess={saveSession} onError={showError} notice={notice} />
  }

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
        <div className="brand"><span className="brand-mark"><Navigation size={17} fill="currentColor" /></span><span>cityride<span className="brand-period">.</span></span></div>
        <div className="workspace-label">YOUR SPACE</div>
        <nav className="nav-list">
          {navItems.map(({ id, label, icon: Icon }) => (
            <button key={id} className={`nav-item ${view === id ? 'nav-active' : ''}`} onClick={() => { setView(id); setMobileNav(false) }}>
              <Icon size={18} strokeWidth={1.8} /><span>{label}</span>
              {id === 'trips' && rides.some((ride) => ['ACCEPTED', 'IN_PROGRESS'].includes(ride.status)) && <i className="nav-dot" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="safety-card"><span className="safety-icon"><ShieldCheck size={17} /></span><strong>Your safety comes first</strong><p>Every ride is backed by 24/7 support.</p><button onClick={() => setView('help')}>Learn about safety <ArrowRight size={13} /></button></div>
          <div className="profile-row">
            <div className="avatar">{session.user.fullName.split(' ').map((part) => part[0]).slice(0, 2).join('')}</div>
            <div className="profile-copy"><strong>{session.user.fullName}</strong><span>{session.user.role.toLowerCase()} account</span></div>
            <button className="icon-button signout" title="Sign out" onClick={signOut}><LogOut size={16} /></button>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <button className="mobile-menu icon-button" onClick={() => setMobileNav(!mobileNav)} aria-label="Open menu"><Menu size={19} /></button>
          <div className="breadcrumb">Workspace <span>/</span> <b>{navItems.find((item) => item.id === view)?.label ?? 'Help center'}</b></div>
          <div className="topbar-right"><span className="live-indicator"><i /> All systems operational</span><button className="help-button" onClick={() => setView('help')}><CircleHelp size={17} /></button></div>
        </header>
        {notice && <div className={`toast ${notice.includes('wrong') || notice.includes('incorrect') || notice.includes('not ') ? 'toast-error' : ''}`}><span>{notice}</span><button onClick={() => setNotice('')}><X size={15} /></button></div>}
        <div className="page-wrap">
          {view === 'home' && (session.user.role === 'DRIVER'
            ? <DriverHome user={session.user} rides={rides} available={available} rankings={driverRankings} onAvailability={(value) => act(async () => {
              const result = await api.availability(session.token, value)
              const next = { ...session, user: { ...session.user, available: result.available } }
              saveSession(next)
            }, value ? 'You are online and receiving nearby requests.' : 'You are now offline.')} onAccept={(id) => act(() => api.accept(session.token, id), 'Ride accepted — the rider has been notified.')} onDecline={(id) => act(() => api.decline(session.token, id), 'Ride request declined.')} onStatus={(id, status) => act(() => api.status(session.token, id, status), `Trip marked ${status.toLowerCase().replace('_', ' ')}.`)} />
            : session.user.role === 'ADMIN'
              ? <AdminHome user={session.user} summary={adminSummary} rides={adminRides} rankings={driverRankings} tickets={supportTickets} promos={promoCodes} onCreatePromo={(promo) => act(() => api.createPromoCode(session.token, promo), 'Promo code created.')} onPromoEnabled={(code, enabled) => act(() => api.setPromoEnabled(session.token, code, enabled), `Promo ${code} ${enabled ? 'enabled' : 'disabled'}.`)} onTicketStatus={(id, status) => act(() => api.updateSupportTicket(session.token, id, status), `Support ticket marked ${status.toLowerCase()}.`)} />
              : <RiderHome user={session.user} token={session.token} rides={rides} liveLocations={liveLocations} onDownloadReceipt={(id) => act(() => downloadReceipt(session.token, id), 'Receipt downloaded.')} onEmailReceipt={(id) => act(() => api.emailReceipt(session.token, id), 'Receipt emailed to your account address.')} onSos={(id) => act(() => api.sos(session.token, id), 'Emergency alert sent to support and trip participants.')} onBook={(trip) => act(() => api.book(session.token, trip), 'Your ride request was sent to nearby online drivers.')} onViewAll={() => setView('trips')} onDialog={setDialog} />)}
          {view === 'trips' && <TripsView rides={rides} role={session.user.role} onDownloadReceipt={(id) => act(() => downloadReceipt(session.token, id), 'Receipt downloaded.')} onEmailReceipt={(id) => act(() => api.emailReceipt(session.token, id), 'Receipt emailed to your account address.')} onAction={(ride, action) => setDialog({ type: action, ride })} onStatus={(id, status) => act(() => api.status(session.token, id, status), `Trip marked ${status.toLowerCase().replace('_', ' ')}.`)} />}
          {view === 'earnings' && <EarningsView rides={rides} />}
          {view === 'admin' && <AdminHome user={session.user} summary={adminSummary} rides={adminRides} rankings={driverRankings} tickets={supportTickets} promos={promoCodes} onCreatePromo={(promo) => act(() => api.createPromoCode(session.token, promo), 'Promo code created.')} onPromoEnabled={(code, enabled) => act(() => api.setPromoEnabled(session.token, code, enabled), `Promo ${code} ${enabled ? 'enabled' : 'disabled'}.`)} onTicketStatus={(id, status) => act(() => api.updateSupportTicket(session.token, id, status), `Support ticket marked ${status.toLowerCase()}.`)} />}
          {view === 'help' && <HelpView token={session.token} onSuccess={() => setNotice('Support request submitted. Our team will follow up.')} onError={showError} />}
        </div>
      </main>
      {mobileNav && <button className="mobile-scrim" onClick={() => setMobileNav(false)} aria-label="Close menu" />}
      {dialog?.type === 'rating' && <RatingDialog ride={dialog.ride} onClose={() => setDialog(null)} onSubmit={(rating, review) => act(() => api.rate(session.token, dialog.ride.id, rating, review), 'Thanks for sharing your feedback!')} loading={loading} />}
      {dialog?.type === 'payment' && <PaymentDialog ride={dialog.ride} onClose={() => setDialog(null)} onPay={() => act(async () => {
        const { checkoutUrl } = await api.pay(session.token, dialog.ride.id)
        window.location.assign(checkoutUrl)
      })} loading={loading} />}
      {dialog?.type === 'cancel' && <CancelDialog ride={dialog.ride} onClose={() => setDialog(null)} onCancel={() => act(() => api.status(session.token, dialog.ride.id, 'CANCELLED'), 'Your ride request was cancelled.')} loading={loading} />}
    </div>
  )
}

function AuthScreen({ mode, setMode, onSuccess, onError, notice }) {
  const [role, setRole] = useState('RIDER')
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const isRegister = mode === 'register'
  const submit = async (event) => {
    event.preventDefault()
    setLoading(true)
    try {
      const data = isRegister
        ? await api.register(form.fullName, form.email, form.password, role)
        : await api.login(form.email, form.password)
      onSuccess(data)
    } catch (error) { onError(error) } finally { setLoading(false) }
  }
  const set = (key) => (event) => setForm((value) => ({ ...value, [key]: event.target.value }))

  return (
    <div className="auth-layout">
      <section className="auth-panel">
        <div className="auth-brand brand"><span className="brand-mark"><Navigation size={17} fill="currentColor" /></span><span>cityride<span className="brand-period">.</span></span></div>
        <div className="auth-form-wrap">
          <div className="eyebrow"><span /> YOUR CITY, YOUR RIDE</div>
          <h1>{isRegister ? 'The city is yours.' : 'Welcome back.'}</h1>
          <p className="auth-subtitle">{isRegister ? 'Create your CityRide account and make every trip a little easier.' : 'A better ride is just around the corner.'}</p>
          <form className="auth-form" onSubmit={submit}>
            {isRegister && <label>Full name<input required maxLength="100" autoComplete="name" placeholder="Alex Morgan" value={form.fullName} onChange={set('fullName')} /></label>}
            <label>Email address<input required type="email" autoComplete="email" placeholder="you@example.com" value={form.email} onChange={set('email')} /></label>
            <label>Password<input required type="password" minLength="8" autoComplete={isRegister ? 'new-password' : 'current-password'} placeholder="At least 8 characters" value={form.password} onChange={set('password')} /></label>
            {isRegister && <div className="role-choice"><span>I'm joining as a</span><div><button type="button" className={role === 'RIDER' ? 'role-selected' : ''} onClick={() => setRole('RIDER')}><MapPin size={14} /> Rider</button><button type="button" className={role === 'DRIVER' ? 'role-selected' : ''} onClick={() => setRole('DRIVER')}><CarFront size={15} /> Driver</button></div></div>}
            {notice && <div className="inline-error">{notice}</div>}
            <button className="primary-button auth-submit" disabled={loading}>{loading ? 'One moment…' : isRegister ? 'Create my account' : 'Sign in'}<ArrowRight size={16} /></button>
          </form>
          {!isRegister && import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true' && <a className="google-login" href="/oauth2/authorization/google">Continue with Google</a>}
          <div className="auth-switch">{isRegister ? 'Already have an account?' : 'New to CityRide?'} <button onClick={() => { setMode(isRegister ? 'login' : 'register'); setForm({ fullName: '', email: '', password: '' }) }}>{isRegister ? 'Sign in' : 'Create an account'}</button></div>
          {!isRegister && <div className="demo-box"><div className="demo-heading"><Sparkles size={14} /> QUICK DEMO ACCESS</div><button onClick={() => setForm({ email: 'rider@cityride.com', password: 'RideDemo2026!' })}><MapPin size={14} /> Rider demo <span>rider@cityride.com</span><ArrowRight size={14} /></button><button onClick={() => setForm({ email: 'driver@cityride.com', password: 'RideDemo2026!' })}><CarFront size={15} /> Driver demo <span>driver@cityride.com</span><ArrowRight size={14} /></button><button onClick={() => setForm({ email: 'admin@cityride.com', password: 'RideDemo2026!' })}><ShieldCheck size={15} /> Admin demo <span>admin@cityride.com</span><ArrowRight size={14} /></button><small>Demo password: <b>RideDemo2026!</b></small></div>}
          <div className="auth-foot"><ShieldCheck size={14} /> Your information is encrypted and secure.</div>
        </div>
        <footer className="auth-footer">© 2026 CityRide, Inc. <span>·</span> Made for the way you move.</footer>
      </section>
      <section className="auth-visual">
        <div className="visual-top"><span><i /> ALWAYS MOVING FOR YOU</span><span>EST. 2024</span></div>
        <div className="visual-map"><MapArtwork /></div>
        <div className="visual-quote"><div className="quote-stars">★★★★★</div><h2>“The best part of my day<br />is not having to think about<br />how I’ll get there.”</h2><div className="quote-person"><div className="quote-avatar">S</div><div><b>Sam Rivera</b><span>Riding with CityRide since 2024</span></div><BadgeCheck size={17} /></div></div>
        <div className="visual-bottom"><span>BENGALURU · MUMBAI · DELHI</span><span>01 — 03</span></div>
      </section>
    </div>
  )
}

function MapArtwork() {
  return <div className="map-art">
    <div className="map-gridlines" />
    <div className="map-road road-one" /><div className="map-road road-two" /><div className="map-road road-three" />
    <div className="map-road road-four" /><div className="map-road road-five" /><div className="map-road road-six" />
    <div className="map-block block-one" /><div className="map-block block-two" /><div className="map-block block-three" />
    <div className="map-block block-four" /><div className="map-block block-five" />
    <div className="map-route"><svg viewBox="0 0 480 350" preserveAspectRatio="none" aria-hidden="true"><path className="route-outline" d="M70 280 C125 270 125 202 199 205 S268 231 306 164 S377 119 415 64" /><path d="M70 280 C125 270 125 202 199 205 S268 231 306 164 S377 119 415 64" /></svg></div>
    <div className="map-pin start-pin"><span><MapPin size={17} fill="currentColor" /></span></div><div className="map-pin end-pin"><span><MapPin size={18} fill="currentColor" /></span></div>
    <div className="map-car"><CarFront size={17} /></div><span className="map-label label-start">MG ROAD</span><span className="map-label label-end">CUBBON PARK</span>
    <span className="map-street">VITTAL MALLYA RD</span><span className="map-street street-two">M G ROAD</span><div className="map-compass"><Navigation size={15} fill="currentColor" /><span>N</span></div>
  </div>
}

function PageIntro({ eyebrow, title, description, action }) {
  return <div className="page-intro"><div><div className="eyebrow"><span /> {eyebrow}</div><h1>{title}</h1><p>{description}</p></div>{action}</div>
}

function RiderHome({ user, token, rides, liveLocations, onDownloadReceipt, onEmailReceipt, onSos, onBook, onViewAll, onDialog }) {
  const [trip, setTrip] = useState({ pickup: '', destination: '', distanceKm: '' })
  const [formError, setFormError] = useState('')
  const [fare, setFare] = useState(null)
  const [routePlan, setRoutePlan] = useState(null)
  const [promoCode, setPromoCode] = useState('')
  const [estimating, setEstimating] = useState(false)
  const [locating, setLocating] = useState(false)
  const activeRide = rides.find((ride) => ['REQUESTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(ride.status) && (!ride.paid || !ride.rating))
  const set = (key) => (event) => {
    setTrip((value) => ({ ...value, [key]: event.target.value }))
    setFormError('')
    setFare(null)
    setRoutePlan(null)
  }
  useEffect(() => {
    if (!activeRide || routePlan) return
    let cancelled = false
    planRoute(activeRide.pickup, activeRide.destination)
      .then((route) => { if (!cancelled) setRoutePlan(route) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [activeRide?.id])
  const estimate = async () => {
    if (!trip.pickup.trim() || !trip.destination.trim()) {
      setFormError('Enter both a pickup and destination to plan your route and fare.')
      return
    }
    setEstimating(true)
    try {
      const route = await planRoute(trip.pickup, trip.destination)
      setRoutePlan(route)
      const result = await api.estimate(token, route.distanceKm, promoCode)
      setFare(result.fare)
    } catch (error) { setFormError(error.message) } finally { setEstimating(false) }
  }
  const submit = async (event) => {
    event.preventDefault()
    setFormError('')
    try {
      const route = routePlan ?? await planRoute(trip.pickup, trip.destination)
      setRoutePlan(route)
      const succeeded = await onBook({
        pickup: trip.pickup,
        destination: trip.destination,
        distanceKm: route.distanceKm,
        pickupLatitude: route.pickupLatitude,
        pickupLongitude: route.pickupLongitude,
        destinationLatitude: route.destinationLatitude,
        destinationLongitude: route.destinationLongitude,
        promoCode: promoCode.trim() || null,
      })
      if (succeeded) {
        setTrip({ pickup: '', destination: '', distanceKm: '' })
        setPromoCode('')
        setFare(null)
        setRoutePlan(null)
      }
    } catch (error) { setFormError(error.message) }
  }
  const locate = () => {
    if (!navigator.geolocation) {
      setFormError('Location is not available in this browser. Enter your pickup location instead.')
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setTrip((value) => ({
          ...value,
          pickup: `Current location (${coords.latitude.toFixed(3)}, ${coords.longitude.toFixed(3)})`,
        }))
        setRoutePlan(null)
        setFare(null)
        setFormError('')
        setLocating(false)
      },
      () => {
        setFormError('We couldn’t access your location. Check your browser permission or enter it manually.')
        setLocating(false)
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 120000 },
    )
  }

  return <>
    <PageIntro eyebrow="GOOD THINGS ARE AROUND THE CORNER" title={`Hey, ${user.fullName.split(' ')[0]}.`} description="Where are we taking you today?" />
    <div className="rider-grid">
      <section className="booking-card">
        <div className="section-heading"><div><span className="heading-kicker">PLAN YOUR RIDE</span><h2>Let’s get you there.</h2></div><span className="ride-type"><CarFront size={17} /> CityRide <ChevronDown size={13} /></span></div>
        <div className="booking-content">
          <div className="location-inputs">
            <label className="location-field"><span className="location-mark pickup-mark" /><span className="location-copy"><small>PICKUP LOCATION</small><input placeholder="Where should we pick you up?" value={trip.pickup} onChange={set('pickup')} maxLength="180" /></span><button className="locate-button" title="Use current location" type="button" onClick={locate} disabled={locating}><LocateFixed size={16} /></button></label>
            <div className="location-connector"><i /></div>
            <label className="location-field"><span className="location-mark destination-mark" /><span className="location-copy"><small>YOUR DESTINATION</small><input placeholder="Where are you headed?" value={trip.destination} onChange={set('destination')} maxLength="180" /></span><span className="field-end-icon"><ArrowDownLeft size={16} /></span></label>
          </div>
          <div className="distance-row"><div className="distance-display"><span>ROUTE DISTANCE</span><strong>{routePlan ? `${routePlan.distanceKm} km` : 'Calculated from route'}</strong></div><div className="distance-note"><Clock3 size={15} /><span>{routePlan ? `Estimated ${routePlan.durationMinutes} min` : 'Driving distance and ETA'}</span></div></div>
          <label className="promo-field">PROMO CODE<input placeholder="WELCOME10" value={promoCode} maxLength="40" onChange={(event) => { setPromoCode(event.target.value.toUpperCase()); setFare(null) }} /><small>Optional · 10% off with WELCOME10</small></label>
          {formError && <div className="inline-error form-error">{formError}</div>}
          {fare !== null && <div className="fare-preview"><span><Banknote size={16} /> Estimated fare</span><b>{money(fare)}</b></div>}
          <div className="booking-actions"><button className="secondary-button" onClick={estimate} disabled={estimating}>{estimating ? 'Planning route…' : 'Get route & fare'}</button><button className="primary-button" onClick={submit} disabled={estimating}>Find my ride <ArrowRight size={15} /></button></div>
          <div className="booking-assurance"><ShieldCheck size={14} /><span>Route, distance, and ETA calculated from OpenStreetMap.</span></div>
        </div>
      </section>
      <section className="map-card"><div className="map-card-head"><div><span className="heading-kicker">YOUR CITY, CONNECTED</span><h2>{routePlan ? `${routePlan.distanceKm} km · ${routePlan.durationMinutes} min` : 'Plan your route'}</h2></div><span className="live-pill"><i /> LIVE MAP</span></div><LiveMap route={routePlan ?? activeRide} driverLocation={activeRide ? liveLocations[activeRide.id] ?? (activeRide.driverLatitude != null ? [activeRide.driverLatitude, activeRide.driverLongitude] : null) : null} /><div className="map-card-bottom"><span><i className="map-legend-dot" /> Online drivers only</span><span className="map-updated">OpenStreetMap</span></div></section>
    </div>
    <div className="below-grid">
      <section className="recent-card">
        <div className="card-title-row"><div><span className="heading-kicker">YOUR CITYRIDE ACTIVITY</span><h2>Recent rides</h2></div><button className="text-button" onClick={onViewAll}>View all <ArrowRight size={14} /></button></div>
        {rides.length === 0 ? <div className="empty-state"><span className="empty-icon"><Navigation size={20} /></span><div><b>Your next good trip starts here.</b><span>Request a ride and your journey will show up here.</span></div></div> : <div className="ride-list">{rides.slice(0, 3).map((ride) => <RideRow key={ride.id} ride={ride} onAction={(action) => onDialog({ type: action, ride })} onDownloadReceipt={onDownloadReceipt} onEmailReceipt={onEmailReceipt} />)}</div>}
      </section>
      <section className="perk-card"><div className="perk-icon"><ShieldCheck size={18} /></div><span className="heading-kicker">SAFETY & TRANSPARENCY</span><h2>Your route.<br />Your trip.</h2><p>Review the route and estimated travel time before requesting. Share live trip status with your driver.</p><div className="driver-completion"><span>FARE FORMULA</span><b>₹50 base + ₹15/km · ₹100 minimum</b></div></section>
    </div>
    {activeRide && <><TripStatusBanner ride={activeRide} onAction={(action) => onDialog({ type: action, ride: activeRide })} />{['ACCEPTED', 'IN_PROGRESS'].includes(activeRide.status) && <button className="sos-button" onClick={() => onSos(activeRide.id)}>SOS · Contact emergency support</button>}</>}
  </>
}

function DriverHome({ user, rides, available, rankings, onAvailability, onAccept, onDecline, onStatus }) {
  const earned = rides.filter((ride) => ride.status === 'COMPLETED' && ride.paid).reduce((sum, ride) => sum + Number(ride.fare), 0)
  const completed = rides.filter((ride) => ride.status === 'COMPLETED').length
  return <>
    <PageIntro eyebrow="DRIVER DASHBOARD" title={`Good morning, ${user.fullName.split(' ')[0]}.`} description="Your next great ride is just around the corner." action={<button className={`availability-pill ${user.available ? '' : 'availability-offline'}`} onClick={() => onAvailability(!user.available)}><i /> {user.available ? "You're online" : "You're offline"} <ChevronDown size={14} /></button>} />
    <div className="stat-grid"><StatCard label="PAID EARNINGS" value={money(earned)} detail="From completed, paid trips" icon={Wallet} /><StatCard label="COMPLETED RIDES" value={String(completed).padStart(2, '0')} detail="All-time trips completed" icon={CarFront} /><StatCard label="YOUR RATING" value={user.ratingCount ? user.averageRating.toFixed(2) : '—'} detail={`${user.ratingCount ?? 0} rider ratings · rank ${rankings.findIndex((driver) => driver.driverId === user.id) + 1 || '—'}`} icon={Star} accent /></div>
    <div className="driver-main-grid"><section className="recent-card driver-requests"><div className="card-title-row"><div><span className="heading-kicker">NEARBY REQUESTS</span><h2>Ride requests <span className="count-badge">{available.length}</span></h2></div><span className="live-pill"><i /> LIVE</span></div>{available.length === 0 ? <div className="empty-state"><span className="empty-icon"><Crosshair size={20} /></span><div><b>{user.available ? "You're all caught up." : 'You are offline.'}</b><span>{user.available ? 'Nearby requests will appear here.' : 'Go online to receive nearby ride requests.'}</span></div></div> : <div className="request-list">{available.map((ride) => <article className="request-card" key={ride.id}><div className="request-route"><span className="location-mark pickup-mark" /><div><small>TRIP DISTANCE · {ride.distanceKm} KM</small><b>{ride.pickup}</b><ArrowDown size={13} /><small>DROP-OFF</small><b>{ride.destination}</b></div></div><div className="request-meta"><div><small>EST. FARE</small><b>{money(ride.fare)}</b></div><div><small>REQUESTED</small><b>{dateLabel(ride.createdAt)}</b></div><button className="secondary-button" onClick={() => onDecline(ride.id)}>Decline</button><button className="primary-button" onClick={() => onAccept(ride.id)}>Accept ride <ArrowRight size={14} /></button></div></article>)}</div>}</section><section className="perk-card driver-tip"><div className="perk-icon"><Sparkles size={18} /></div><span className="heading-kicker">DRIVER TIP</span><h2>Little things<br />go a long way.</h2><p>A friendly hello and a smooth ride are what make a five-star trip.</p><div className="driver-completion"><span>YOUR COMPLETED TRIPS</span><b>{completed}<small> trips</small></b></div></section></div>
    <section className="recent-card driver-active"><div className="card-title-row"><div><span className="heading-kicker">YOUR CURRENT SHIFT</span><h2>Active & recent trips</h2></div></div>{rides.length === 0 ? <div className="empty-state"><div><b>No trips yet this shift.</b><span>Accept your first request when you're ready.</span></div></div> : <div className="ride-list">{rides.slice(0, 5).map((ride) => <DriverRideRow key={ride.id} ride={ride} onStatus={onStatus} />)}</div>}</section>
  </>
}

function StatCard({ label, value, detail, icon: Icon, accent }) {
  return <div className={`stat-card ${accent ? 'stat-accent' : ''}`}><div className="stat-top"><span>{label}</span><Icon size={18} /></div><strong>{value}</strong><small>{accent && <Star size={11} fill="currentColor" />} {detail}</small></div>
}

function TripStatusBanner({ ride, onAction }) {
  if (ride.status === 'COMPLETED' && ride.paid && ride.rating) return null
  if (ride.status === 'ARRIVED') {
    return <div className="trip-status-banner trip-arrival-banner" role="status" aria-live="polite">
      <span className="status-pulse"><MapPin size={17} /></span>
      <span><b>Your driver has arrived</b><small>{ride.driverName ? `${ride.driverName} is waiting at ` : 'Your driver is waiting at '}{ride.pickup}</small></span>
      <span className="arrival-badge">DRIVER ARRIVED</span>
    </div>
  }
  const statusText = {
    REQUESTED: 'Looking for your driver',
    ACCEPTED: 'Your driver is on the way',
    IN_PROGRESS: "You're on your way",
    COMPLETED: ride.paid ? 'Rate your ride' : 'Your trip is complete',
  }
  return <button className="trip-status-banner" onClick={() => ride.status === 'COMPLETED' ? onAction(ride.paid ? 'rating' : 'payment') : undefined}><span className={`status-pulse status-${ride.status.toLowerCase()}`}><CarFront size={16} /></span><span><b>{statusText[ride.status]}</b><small>{ride.pickup} <ArrowRight size={11} /> {ride.destination}</small></span>{ride.status === 'COMPLETED' && <span className="banner-action">{ride.paid ? 'Leave a rating' : `Pay ${money(ride.fare)}`} <ArrowRight size={14} /></span>}{ride.status !== 'COMPLETED' && <span className="banner-live"><i /> UPDATING LIVE</span>}</button>
}

function RideRow({ ride, onAction, onDownloadReceipt, onEmailReceipt }) {
  const completed = ride.status === 'COMPLETED'
  const cancellable = ['REQUESTED', 'ACCEPTED'].includes(ride.status)
  return <div className="ride-row"><span className={`ride-icon ${completed ? 'ride-icon-done' : ''}`}><CarFront size={17} /></span><div className="ride-route-copy"><b>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</b><span>{dateLabel(ride.createdAt)} · {ride.distanceKm} km{ride.driverName ? ` · Driver ${ride.driverName}${ride.driverRatingCount ? ` ★ ${ride.driverAverageRating.toFixed(2)} (${ride.driverRatingCount})` : ''}` : ''}</span></div><span className={`status-chip status-chip-${ride.status.toLowerCase()}`}>{ride.status.replace('_', ' ').toLowerCase()}</span><strong className="ride-fare">{money(ride.fare)}</strong>{completed && !ride.paid && <button className="mini-action" onClick={() => onAction('payment')}>Pay</button>}{completed && ride.paid && !ride.rating && <button className="mini-action" onClick={() => onAction('rating')}>Rate</button>}{completed && ride.paid && <><button className="mini-action" onClick={() => onDownloadReceipt(ride.id)}>PDF receipt</button><button className="mini-action" onClick={() => onEmailReceipt(ride.id)}>Email receipt</button></>}{cancellable && <button className="mini-action" onClick={() => onAction('cancel')}>Cancel</button>}{ride.paid && <span className="paid-mark"><Check size={13} /></span>}</div>
}

function DriverRideRow({ ride, onStatus }) {
  const next = ride.status === 'ACCEPTED' || ride.status === 'ARRIVED'
    ? ['IN_PROGRESS', 'Start trip']
    : ride.status === 'IN_PROGRESS' ? ['COMPLETED', 'Complete trip'] : null
  return <div className="ride-row"><span className="ride-icon"><CarFront size={17} /></span><div className="ride-route-copy"><b>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</b><span>{dateLabel(ride.createdAt)} · {ride.distanceKm} km</span></div><span className={`status-chip status-chip-${ride.status.toLowerCase()}`}>{ride.status.replace('_', ' ').toLowerCase()}</span><strong className="ride-fare">{money(ride.fare)}</strong>{next && <button className="mini-action" onClick={() => onStatus(ride.id, next[0])}>{next[1]}</button>}</div>
}

function TripsView({ rides, role, onAction, onStatus, onDownloadReceipt, onEmailReceipt }) {
  const [filter, setFilter] = useState('ALL')
  const visible = filter === 'ALL' ? rides : rides.filter((ride) => ride.status === filter)
  return <><PageIntro eyebrow="EVERY MILE, REMEMBERED" title="Your trips." description="All your CityRide journeys, in one place." /><section className="recent-card trips-card"><div className="filter-tabs">{[['ALL', 'All trips'], ['IN_PROGRESS', 'In progress'], ['COMPLETED', 'Completed'], ['CANCELLED', 'Cancelled']].map(([key, title]) => <button key={key} onClick={() => setFilter(key)} className={filter === key ? 'filter-active' : ''}>{title}{key === 'ALL' && <span>{rides.length}</span>}</button>)}</div>{visible.length === 0 ? <div className="empty-state trips-empty"><span className="empty-icon"><CarFront size={20} /></span><div><b>No rides in this filter yet.</b><span>{filter === 'ALL' ? 'Once you book a ride, it will find its way here.' : 'Check another filter to see more of your journey.'}</span></div></div> : <div className="ride-list">{visible.map((ride) => role === 'DRIVER' ? <DriverRideRow key={ride.id} ride={ride} onStatus={onStatus} /> : <RideRow key={ride.id} ride={ride} onAction={(action) => onAction(ride, action)} onDownloadReceipt={onDownloadReceipt} onEmailReceipt={onEmailReceipt} />)}</div>}</section><div className="trip-note"><ShieldCheck size={15} /> All your ride details are private and only visible to you.</div></>
}

function EarningsView({ rides }) {
  const completed = rides.filter((ride) => ride.status === 'COMPLETED')
  const paid = completed.filter((ride) => ride.paid)
  const total = paid.reduce((sum, ride) => sum + Number(ride.fare), 0)
  const average = paid.length ? total / paid.length : 0
  return <><PageIntro eyebrow="YOUR HARD WORK, PAID OFF" title="Earnings." description="A clear view of every rupee you've earned on the road." /><div className="earnings-grid"><StatCard label="TOTAL EARNED" value={money(total)} detail="From completed, paid trips" icon={Banknote} /><StatCard label="TRIPS COMPLETED" value={String(completed.length).padStart(2, '0')} detail="Keep the momentum going" icon={CarFront} /><StatCard label="AVERAGE PER TRIP" value={money(average)} detail="Your average ride earnings" icon={Sparkles} /></div><section className="recent-card earnings-history"><div className="card-title-row"><div><span className="heading-kicker">THE NUMBERS, IN DETAIL</span><h2>Recent earnings</h2></div><button className="secondary-button export-button" onClick={() => exportTrips(completed)}><Download size={14} /> Export CSV</button></div>{completed.length ? <div className="ride-list">{completed.map((ride) => <div className="ride-row" key={ride.id}><span className="ride-icon ride-icon-done"><Check size={16} /></span><div className="ride-route-copy"><b>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</b><span>{dateLabel(ride.createdAt)} · {ride.distanceKm} km</span></div><span className={`status-chip ${ride.paid ? 'status-chip-completed' : 'status-chip-requested'}`}>{ride.paid ? 'paid' : 'payment pending'}</span><strong className="ride-fare">{money(ride.fare)}</strong></div>)}</div> : <div className="empty-state"><span className="empty-icon"><Wallet size={20} /></span><div><b>Your next payout starts with your first ride.</b><span>Completed trips will show up here with a clear earnings breakdown.</span></div></div>}</section></>
}

function AdminHome({ user, summary, rides, rankings, tickets, promos, onTicketStatus, onCreatePromo, onPromoEnabled }) {
  const [promoForm, setPromoForm] = useState({ code: '', discountPercent: '10', maxRedemptions: '' })
  return <>
    <PageIntro eyebrow="CITYRIDE OPERATIONS" title="System overview." description={`A live snapshot of the network, ${user.fullName.split(' ')[0]}.`} />
    <div className="stat-grid">
      <StatCard label="ALL RIDES" value={String(summary?.totalRides ?? 0).padStart(2, '0')} detail={`${summary?.activeRides ?? 0} currently active`} icon={CarFront} />
      <StatCard label="REGISTERED RIDERS" value={String(summary?.totalRiders ?? 0).padStart(2, '0')} detail={`${summary?.totalDrivers ?? 0} driver accounts`} icon={MapPin} />
      <StatCard label="PAID REVENUE" value={money(summary?.paidRevenue)} detail={`${summary?.completedRides ?? 0} trips completed`} icon={Banknote} />
    </div>
    <section className="recent-card earnings-history"><div className="card-title-row"><div><span className="heading-kicker">DRIVER LEADERBOARD</span><h2>Top-rated drivers</h2></div></div>{rankings.length ? <div className="ride-list">{rankings.slice(0, 5).map((driver, index) => <div className="ride-row" key={driver.driverId}><span className="ride-icon ride-icon-done"><Star size={16} /></span><div className="ride-route-copy"><b>#{index + 1} {driver.driverName}</b><span>{driver.completedRides} trips completed · {driver.ratingCount} rider ratings</span></div><strong className="ride-fare">{driver.ratingCount ? `${driver.averageRating.toFixed(2)} / 5` : 'No ratings'}</strong></div>)}</div> : <div className="empty-state"><div><b>No driver ratings yet.</b><span>Ratings appear after riders review completed trips.</span></div></div>}</section>
    <section className="recent-card earnings-history"><div className="card-title-row"><div><span className="heading-kicker">CUSTOMER SUPPORT</span><h2>Support tickets ({tickets.length})</h2></div></div>{tickets.length ? <div className="ride-list">{tickets.map((ticket) => <article className="support-ticket-row" key={ticket.id}><div className="ride-route-copy"><b>{ticket.subject} · {ticket.userName}</b><span>{ticket.userEmail}{ticket.rideId ? ` · Trip ${ticket.rideId}` : ''} · {dateLabel(ticket.createdAt)}</span><p>{ticket.message}</p></div><select aria-label={`Ticket ${ticket.id} status`} value={ticket.status} onChange={(event) => onTicketStatus(ticket.id, event.target.value)}><option>OPEN</option><option>IN_PROGRESS</option><option>RESOLVED</option></select></article>)}</div> : <div className="empty-state"><div><b>No support tickets.</b><span>Customer requests will appear here.</span></div></div>}</section>
    <section className="recent-card earnings-history"><div className="card-title-row"><div><span className="heading-kicker">PROMOTIONS</span><h2>Promo codes</h2></div></div><form className="promo-admin-form" onSubmit={(event) => { event.preventDefault(); onCreatePromo({ code: promoForm.code, discountPercent: Number(promoForm.discountPercent), maxRedemptions: promoForm.maxRedemptions ? Number(promoForm.maxRedemptions) : null }); setPromoForm({ code: '', discountPercent: '10', maxRedemptions: '' }) }}><label>Code<input required maxLength="40" value={promoForm.code} onChange={(event) => setPromoForm({ ...promoForm, code: event.target.value.toUpperCase() })} /></label><label>Discount %<input required type="number" min="0.01" max="100" step="0.01" value={promoForm.discountPercent} onChange={(event) => setPromoForm({ ...promoForm, discountPercent: event.target.value })} /></label><label>Max uses<input type="number" min="1" value={promoForm.maxRedemptions} onChange={(event) => setPromoForm({ ...promoForm, maxRedemptions: event.target.value })} /></label><button className="primary-button">Create promotion</button></form>{promos.map((promo) => <div className="ride-row" key={promo.code}><span className="ride-icon"><Sparkles size={16} /></span><div className="ride-route-copy"><b>{promo.code} · {promo.discountPercent}% off</b><span>{promo.redemptionCount} redeemed{promo.maxRedemptions ? ` of ${promo.maxRedemptions}` : ''}{promo.expiresAt ? ` · expires ${dateLabel(promo.expiresAt)}` : ''}</span></div><button className="secondary-button" onClick={() => onPromoEnabled(promo.code, !promo.enabled)}>{promo.enabled ? 'Disable' : 'Enable'}</button></div>)}</section>
    <section className="recent-card earnings-history"><div className="card-title-row"><div><span className="heading-kicker">NETWORK ACTIVITY</span><h2>All rides</h2></div><span className="live-pill"><i /> REFRESHES LIVE</span></div>{rides.length === 0 ? <div className="empty-state"><div><b>No ride activity yet.</b><span>New rider requests will appear here.</span></div></div> : <div className="ride-list">{rides.map((ride) => <div className="ride-row" key={ride.id}><span className="ride-icon"><CarFront size={17} /></span><div className="ride-route-copy"><b>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</b><span>{ride.riderName} · {ride.driverName ?? 'Finding a driver'} · {dateLabel(ride.createdAt)}</span></div><span className={`status-chip status-chip-${ride.status.toLowerCase()}`}>{ride.status.replace('_', ' ').toLowerCase()}</span><strong className="ride-fare">{money(ride.fare)}</strong></div>)}</div>}</section>
  </>
}

function exportTrips(trips) {
  const rows = [['Date', 'Pickup', 'Destination', 'Distance (km)', 'Fare (INR)', 'Status'], ...trips.map((ride) => [dateLabel(ride.createdAt), ride.pickup, ride.destination, ride.distanceKm, ride.fare, ride.paid ? 'Paid' : 'Pending'])]
  const csv = rows.map((row) => row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'cityride-earnings.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

function HelpView({ token, onSuccess, onError }) {
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)
  const submit = async (event) => {
    event.preventDefault()
    setSending(true)
    try {
      await api.createSupportTicket(token, { subject, message })
      setSubject('')
      setMessage('')
      onSuccess()
    } catch (error) { onError(error) } finally { setSending(false) }
  }
  return <><PageIntro eyebrow="WE'RE HERE FOR YOU" title="How can we help?" description="Create a support ticket and our operations team can track and resolve your request." /><div className="help-grid"><section className="help-card"><span className="help-icon"><CircleHelp size={19} /></span><span className="heading-kicker">CITYRIDE SUPPORT</span><h2>Send us a message.</h2><form className="support-form" onSubmit={submit}><label>Subject<input required maxLength="120" value={subject} onChange={(event) => setSubject(event.target.value)} /></label><label>How can we help?<textarea required maxLength="2000" value={message} onChange={(event) => setMessage(event.target.value)} /></label><button className="primary-button" disabled={sending}>{sending ? 'Submitting…' : 'Create support ticket'} <ArrowRight size={14} /></button></form></section><article className="help-card"><span className="help-icon"><ShieldCheck size={19} /></span><span className="heading-kicker">YOUR SAFETY, ALWAYS</span><h2>Ride with confidence.</h2><p>Use the SOS action during an active trip to send a real-time alert to operations and the trip participants. Contact local emergency services for immediate danger.</p></article><article className="help-card"><span className="help-icon"><CreditCard size={19} /></span><span className="heading-kicker">SECURE PAYMENTS</span><h2>Stripe Checkout.</h2><p>Payment is processed by Stripe. A signed webhook confirms payment before receipts become available.</p></article></div></>
}

async function downloadReceipt(token, id) {
  const response = await fetch(`/api/rides/${id}/receipt`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message || `Receipt download failed (${response.status}).`)
  }
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `cityride-receipt-${id}.pdf`
  anchor.click()
  URL.revokeObjectURL(url)
}

function RatingDialog({ ride, onClose, onSubmit, loading }) {
  const [rating, setRating] = useState(0)
  const [review, setReview] = useState('')
  return <div className="modal-backdrop" onClick={onClose}><section className="modal-card" onClick={(event) => event.stopPropagation()}><button className="modal-close icon-button" onClick={onClose}><X size={17} /></button><div className="modal-icon"><Star size={20} /></div><span className="heading-kicker">A LITTLE THANK YOU</span><h2>How was your ride?</h2><p>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</p><div className="rating-stars">{[1, 2, 3, 4, 5].map((star) => <button key={star} onClick={() => setRating(star)} className={star <= rating ? 'star-filled' : ''} aria-label={`${star} stars`}><Star size={28} fill={star <= rating ? 'currentColor' : 'none'} /></button>)}</div><textarea placeholder="Anything you'd like us to know? (optional)" maxLength="500" value={review} onChange={(event) => setReview(event.target.value)} /><button className="primary-button modal-submit" disabled={!rating || loading} onClick={() => onSubmit(rating, review)}>{loading ? 'Sending…' : 'Send my rating'} <ArrowRight size={14} /></button></section></div>
}

function PaymentDialog({ ride, onClose, onPay, loading }) {
  return <div className="modal-backdrop" onClick={onClose}><section className="modal-card payment-modal" onClick={(event) => event.stopPropagation()}><button className="modal-close icon-button" onClick={onClose}><X size={17} /></button><div className="modal-icon"><CreditCard size={20} /></div><span className="heading-kicker">TRIP PAYMENT</span><h2>Secure Stripe checkout.</h2><p>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</p><div className="payment-breakdown"><span>Ride fare <b>{money(ride.fare)}</b></span><span>Service fee <b>Included</b></span><strong>Total due <b>{money(ride.fare)}</b></strong></div><button className="primary-button modal-submit" disabled={loading} onClick={onPay}>{loading ? 'Opening secure checkout…' : <>Continue to Stripe · {money(ride.fare)} <ArrowRight size={14} /></>}</button><div className="payment-disclaimer"><ShieldCheck size={13} /> Card details are entered securely on Stripe.</div></section></div>
}

function CancelDialog({ ride, onClose, onCancel, loading }) {
  return <div className="modal-backdrop" onClick={onClose}><section className="modal-card" onClick={(event) => event.stopPropagation()}><button className="modal-close icon-button" onClick={onClose}><X size={17} /></button><div className="modal-icon"><X size={19} /></div><span className="heading-kicker">RIDE REQUEST</span><h2>Cancel this ride?</h2><p>{ride.pickup} <ArrowRight size={12} /> {ride.destination}</p><p>You can request another ride whenever you're ready.</p><div className="modal-actions"><button className="secondary-button" onClick={onClose}>Keep my ride</button><button className="primary-button" disabled={loading} onClick={onCancel}>{loading ? 'Cancelling…' : 'Cancel ride'}</button></div></section></div>
}

const root = import.meta.hot?.data.root ?? createRoot(document.getElementById('root'))
if (import.meta.hot) import.meta.hot.dispose((data) => { data.root = root })
root.render(<React.StrictMode><App /></React.StrictMode>)
