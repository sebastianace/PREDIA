import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Plantilla } from './componentes/Plantilla'
import { Activar } from './paginas/Activar'
import { Portal } from './paginas/Portal'
import { Comprador } from './paginas/Comprador'
import { CompradorInmueble } from './paginas/CompradorInmueble'
import { Usuarios } from './paginas/Usuarios'
import { Asistente } from './paginas/Asistente'
import { ClienteDetalle } from './paginas/ClienteDetalle'
import { ClienteForm } from './paginas/ClienteForm'
import { Clientes } from './paginas/Clientes'
import { Expedientes } from './paginas/Expedientes'
import { InmuebleForm } from './paginas/InmuebleForm'
import { Inicio } from './paginas/Inicio'
import { NegocioDetalle } from './paginas/NegocioDetalle'
import { NegocioNuevo } from './paginas/NegocioNuevo'
import { Negocios } from './paginas/Negocios'
import { Reduccion } from './paginas/Reduccion'
import { Recordatorios } from './paginas/Recordatorios'
import { Requisitos } from './paginas/Requisitos'
import { Solicitudes } from './paginas/Solicitudes'
import { Inventario } from './paginas/Inventario'
import { Login } from './paginas/Login'
import { Tasas } from './paginas/Tasas'
import { ProveedorSesion, useSesion } from './sesion/Sesion'

function Rutas() {
  const { usuario, cargando } = useSesion()
  const { pathname } = useLocation()
  // El enlace de invitación es público: se abre con o sin sesión.
  if (pathname === '/activar') return <Activar />
  if (cargando) return <div className="p-6 text-stone-500">Cargando…</div>
  if (!usuario) return <Login />
  // El propietario solo tiene su portal; el resto de rutas lo devuelven a él.
  if (usuario.rol === 'PROPIETARIO') {
    return (
      <Routes>
        <Route path="/portal" element={<Portal />} />
        <Route path="*" element={<Navigate to="/portal" replace />} />
      </Routes>
    )
  }
  // El comprador solo ve lo que su asesor le compartió.
  if (usuario.rol === 'COMPRADOR') {
    return (
      <Routes>
        <Route path="/comprador" element={<Comprador />} />
        <Route path="/comprador/inmuebles/:id" element={<CompradorInmueble />} />
        <Route path="*" element={<Navigate to="/comprador" replace />} />
      </Routes>
    )
  }
  return (
    <Routes>
      <Route element={<Plantilla />}>
        <Route index element={<Inicio />} />
        <Route path="asistente" element={<Asistente />} />
        <Route path="inventario" element={<Inventario />} />
        <Route path="inventario/nuevo" element={<InmuebleForm />} />
        <Route path="inventario/:id/editar" element={<InmuebleForm />} />
        <Route path="clientes" element={<Clientes />} />
        <Route path="clientes/nuevo" element={<ClienteForm />} />
        <Route path="clientes/:id" element={<ClienteDetalle />} />
        <Route path="clientes/:id/editar" element={<ClienteForm />} />
        <Route path="negocios" element={<Negocios />} />
        <Route path="negocios/nuevo" element={<NegocioNuevo />} />
        <Route path="negocios/:id" element={<NegocioDetalle />} />
        <Route path="reduccion" element={<Reduccion />} />
        <Route path="recordatorios" element={<Recordatorios />} />
        <Route path="solicitudes" element={<Solicitudes />} />
        <Route path="requisitos" element={<Requisitos />} />
        <Route path="expedientes" element={<Expedientes />} />
        <Route path="tasas" element={<Tasas />} />
        {usuario.rol === 'ADMIN' && <Route path="usuarios" element={<Usuarios />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <ProveedorSesion>
      <BrowserRouter>
        <Rutas />
      </BrowserRouter>
    </ProveedorSesion>
  )
}
