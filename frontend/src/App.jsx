import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Landing from './pages/Landing';
import Dashboard from './pages/Dashboard';
import PanicMode from './pages/PanicMode';
import TaskDetail from './pages/TaskDetail';
import Settings from './pages/Settings';
import AuthGuard from './components/AuthGuard';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        
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
