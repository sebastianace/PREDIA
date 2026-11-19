import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Plantilla } from './componentes/Plantilla'
import { ClienteDetalle } from './paginas/ClienteDetalle'
import { ClienteForm } from './paginas/ClienteForm'
import { Clientes } from './paginas/Clientes'
import { Expedientes } from './paginas/Expedientes'
import { InmuebleForm } from './paginas/InmuebleForm'
import { Inicio } from './paginas/Inicio'
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
        <Route path="inventario" element={<Inventario />} />
        <Route path="inventario/nuevo" element={<InmuebleForm />} />
        <Route path="inventario/:id/editar" element={<InmuebleForm />} />
        <Route path="clientes" element={<Clientes />} />
        <Route path="clientes/nuevo" element={<ClienteForm />} />
        <Route path="clientes/:id" element={<ClienteDetalle />} />
        <Route path="clientes/:id/editar" element={<ClienteForm />} />
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
