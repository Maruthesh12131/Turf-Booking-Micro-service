import { Outlet } from 'react-router-dom'
import Footer from './Footer'
import Header from './Header'

export default function Layout() {
  return (
    <div className="app">
      <a href="#main" className="skip">Skip to content</a>
      <Header />
      <main id="main" className="container main"><Outlet /></main>
      <Footer />
    </div>
  )
}
