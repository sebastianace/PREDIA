import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Plantilla } from './componentes/Plantilla'
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
import { Inventario } from './paginas/Inventario'
import { Login } from './paginas/Login'
import { Tasas } from './paginas/Tasas'
import { ProveedorSesion, useSesion } from './sesion/Sesion'

function Rutas() {
  const { usuario, cargando } = useSesion()
  if (cargando) return <div className="p-6 text-stone-500">Cargando…</div>
  if (!usuario) return <Login />
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
        <Route path="requisitos" element={<Requisitos />} />
        <Route path="expedientes" element={<Expedientes />} />
        <Route path="tasas" element={<Tasas />} />
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
