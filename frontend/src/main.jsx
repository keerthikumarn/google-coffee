import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import CustomerApp from './customer/CustomerApp.jsx'
import StaffApp from './staff/StaffApp.jsx'

const isStaff = window.location.pathname.startsWith('/staff')

createRoot(document.getElementById('root')).render(
  <StrictMode>{isStaff ? <StaffApp /> : <CustomerApp />}</StrictMode>,
)
