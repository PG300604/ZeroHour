import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Landing from './pages/Landing';
import Dashboard from './pages/Dashboard';
import PanicMode from './pages/PanicMode';
import TaskDetail from './pages/TaskDetail';
import Settings from './pages/Settings';
import AuthGuard from './components/AuthGuard';
import About from './pages/info/About';
import Privacy from './pages/info/Privacy';
import Terms from './pages/info/Terms';
import Security from './pages/info/Security';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/about" element={<About />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="/security" element={<Security />} />
        
        {/* Protected Routes */}
        <Route 
          path="/dashboard" 
          element={
            <AuthGuard>
              <Dashboard />
            </AuthGuard>
          } 
        />
        <Route 
          path="/panic" 
          element={
            <AuthGuard>
              <PanicMode />
            </AuthGuard>
          } 
        />
        <Route 
          path="/task/:id" 
          element={
            <AuthGuard>
              <TaskDetail />
            </AuthGuard>
          } 
        />
        <Route 
          path="/settings" 
          element={
            <AuthGuard>
              <Settings />
            </AuthGuard>
          } 
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
