import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Plantilla } from './componentes/Plantilla'
import { Inicio } from './paginas/Inicio'
import { Inventario } from './paginas/Inventario'
import { Login } from './paginas/Login'
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
