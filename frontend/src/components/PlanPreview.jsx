import { useState } from 'react';
import { Play, Plus, Trash2, Check, Edit3, GripVertical } from 'lucide-react';

export default function PlanPreview({ initialSubtasks = [], onConfirm, isProcessing = false }) {
  const [subtasks, setSubtasks] = useState(() => {
    const list = Array.isArray(initialSubtasks) ? initialSubtasks : [];
    return list.map((s, idx) => ({
      ...s,
      id: s.id || `temp-${idx}`,
      priority: s.priority || 'MEDIUM',
      priorityReason: s.priorityReason || 'Part of plan.'
    }));
  });
  const [editingId, setEditingId] = useState(null);
  const [editTitle, setEditTitle] = useState('');

  // Drag and Drop States
  const [draggedIndex, setDraggedIndex] = useState(null);
  const [dragOverIndex, setDragOverIndex] = useState(null);

  const handleEditStart = (subtask) => {
    setEditingId(subtask.id);
    setEditTitle(subtask.title);
  };

  const handleEditSave = (id) => {
    setSubtasks(subtasks.map(s => s.id === id ? { ...s, title: editTitle } : s));
    setEditingId(null);
  };

  const handleDurationChange = (id, newDuration) => {
    setSubtasks(subtasks.map(s => s.id === id ? { ...s, durationMinutes: Math.max(5, parseInt(newDuration) || 5) } : s));
  };

  const handleDelete = (id) => {
    setSubtasks(subtasks.filter(s => s.id !== id));
  };

  const handleAdd = () => {
    const newSubtask = {
      id: `new-${Date.now()}`,
      title: 'New tactical objective',
      durationMinutes: 30,
      priority: 'MEDIUM',
      priorityReason: 'Added by commander',
      orderIndex: subtasks.length
    };
    setSubtasks([...subtasks, newSubtask]);
  };

  const handleMove = (index, direction) => {
    const nextIndex = index + direction;
    if (nextIndex < 0 || nextIndex >= subtasks.length) return;

    const list = [...subtasks];
    const temp = list[index];
    list[index] = list[nextIndex];
    list[nextIndex] = temp;

    const reordered = list.map((item, idx) => ({ ...item, orderIndex: idx }));
    setSubtasks(reordered);
  };

  // Drag and Drop Handlers
  const handleDragStart = (e, index) => {
    setDraggedIndex(index);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleDragOver = (e, index) => {
    e.preventDefault();
    if (index !== dragOverIndex) {
      setDragOverIndex(index);
    }
  };

  const handleDragEnd = () => {
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const handleDrop = (e, index) => {
    e.preventDefault();
    if (draggedIndex === null || draggedIndex === index) return;

    const list = [...subtasks];
    const draggedItem = list[draggedIndex];
    list.splice(draggedIndex, 1);
    list.splice(index, 0, draggedItem);

    const reordered = list.map((item, idx) => ({ ...item, orderIndex: idx }));
    setSubtasks(reordered);
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const totalMinutes = subtasks.reduce((sum, s) => sum + s.durationMinutes, 0);

  // Compute feasibility
  let feasibility = 'FEASIBLE';
  if (totalMinutes > 240) feasibility = 'DANGEROUS';
  else if (totalMinutes > 180) feasibility = 'RISKY';
  else if (totalMinutes > 120) feasibility = 'TIGHT';

  const feasibilityClass = 
    feasibility === 'FEASIBLE' ? 'bg-[#14B8A6]/10 text-[#14B8A6] border-[#14B8A6]/20' :
    feasibility === 'TIGHT' ? 'bg-blue-500/10 text-blue-400 border-blue-500/20' :
    feasibility === 'RISKY' ? 'bg-amber-500/10 text-amber-400 border-amber-500/20' :
    'bg-[#FF453A]/10 text-[#FF453A] border-[#FF453A]/20 animate-pulse';

  const handlePushToCalendar = () => {
    const cleaned = subtasks.map((s, idx) => ({
      title: s.title,
      durationMinutes: s.durationMinutes,
      priority: s.priority,
      priorityReason: s.priorityReason,
      orderIndex: idx,
      status: 'PENDING'
    }));
    onConfirm(cleaned);
  };

  return (
    <div className="glass-panel p-8 mb-6 border-white/10">
      
      {/* Top Stats Banner */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-white/10 pb-6 mb-6 gap-4">
        <div>
          <h3 className="font-display text-2xl font-black uppercase text-white tracking-tight italic">YOUR SURVIVAL PLAN</h3>
          <p className="text-[10px] font-mono tracking-widest text-[#94a3b8] uppercase mt-1">Review and sequence operational tasks.</p>
        </div>

        <div className="flex gap-4 text-xs items-center">
          <div className="flex flex-col items-end">
            <span className="text-[9px] font-mono tracking-wider text-gray-500 uppercase">Total Duration</span>
            <span className="font-mono font-bold text-white text-base">{totalMinutes} MINS</span>
          </div>

          <span className={`badge ${feasibilityClass}`}>
            {feasibility}
          </span>
        </div>
      </div>

      {/* Subtasks Draggable List */}
      <div className="flex flex-col gap-3 mb-8 max-h-[400px] overflow-y-auto pr-2" id="draggable-list">
        {subtasks.map((s, idx) => {
          const isCritical = s.priority === 'CRITICAL';
          const isHigh = s.priority === 'HIGH';
          const isMedium = s.priority === 'MEDIUM';

          const priorityBadgeClass = 
            isCritical ? 'badge-critical' :
            isHigh ? 'badge-high' :
            isMedium ? 'badge-medium' : 'badge-low';

          const numColorClass = 
            isCritical ? 'text-[#FF453A]' :
            isHigh ? 'text-amber-400' :
            isMedium ? 'text-blue-400' : 'text-gray-500';

          const isOver = idx === dragOverIndex;

          return (
            <div 
              key={s.id}
              draggable={!editingId}
              onDragStart={(e) => handleDragStart(e, idx)}
              onDragOver={(e) => handleDragOver(e, idx)}
              onDragEnd={handleDragEnd}
              onDrop={(e) => handleDrop(e, idx)}
              className={`glass-panel p-4 rounded-xl flex items-center justify-between transition-all gap-4 border border-white/5 bg-white/2 hover:bg-white/4 ${
                isOver ? 'drag-over' : ''
              } ${draggedIndex === idx ? 'opacity-40' : ''}`}
            >
              {/* Drag Handle & Number */}
              <div className="flex items-center gap-3 w-full sm:w-[65%] drag-handle">
                <GripVertical className="w-4 h-4 text-gray-500 group-hover:text-white" />
                <span className={`font-mono text-xl font-bold w-6 ${numColorClass}`}>
                  {(idx + 1).toString().padStart(2, '0')}
                </span>
                
                {editingId === s.id ? (
                  <div className="flex items-center gap-2 w-full" onClick={(e) => e.stopPropagation()}>
                    <input 
                      type="text" 
                      value={editTitle}
                      onChange={(e) => setEditTitle(e.target.value)}
                      className="input-field py-1.5 px-3 text-xs w-full"
                      autoFocus
                    />
                    <button 
                      onClick={() => handleEditSave(s.id)}
                      className="p-1.5 rounded bg-teal-500/20 hover:bg-teal-500/30 text-teal-400"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 group/title w-full">
                    <p className="text-xs font-bold text-gray-200 line-clamp-1">{s.title}</p>
                    <button 
                      onClick={(e) => {
                        e.stopPropagation();
                        handleEditStart(s);
                      }}
                      className="opacity-0 group-hover/title:opacity-100 p-1 text-gray-400 hover:text-white transition-opacity"
                    >
                      <Edit3 className="w-3 h-3" />
                    </button>
                  </div>
                )}
              </div>

              {/* Actions & Configuration */}
              <div className="flex items-center gap-3 w-full sm:w-auto justify-end" onClick={(e) => e.stopPropagation()}>
                {/* Duration Config */}
                <div className="flex items-center gap-1">
                  <input 
                    type="number"
                    min="5"
                    step="5"
                    value={s.durationMinutes}
                    onChange={(e) => handleDurationChange(s.id, e.target.value)}
                    className="w-14 py-1 px-1.5 text-center text-xs bg-white/5 border border-white/10 rounded font-mono"
                  />
                  <span className="text-[10px] font-mono text-gray-500">M</span>
                </div>

                {/* Priority Badge */}
                <span className={`badge text-[8px] px-1.5 py-0.5 rounded ${priorityBadgeClass}`}>
                  {s.priority}
                </span>

                {/* Direction controls & Delete */}
                <div className="flex items-center gap-0.5">
                  <button 
                    onClick={() => handleMove(idx, -1)}
                    disabled={idx === 0}
                    className="p-1 text-gray-500 hover:text-white disabled:opacity-20"
                  >
                    <span className="material-symbols-outlined text-sm">arrow_upward</span>
                  </button>
                  <button 
                    onClick={() => handleMove(idx, 1)}
                    disabled={idx === subtasks.length - 1}
                    className="p-1 text-gray-500 hover:text-white disabled:opacity-20"
                  >
                    <span className="material-symbols-outlined text-sm">arrow_downward</span>
                  </button>
                  <button 
                    onClick={() => handleDelete(s.id)}
                    className="p-1 text-[#FF453A]/60 hover:text-[#FF453A]"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

            </div>
          );
        })}
      </div>

      {/* Graphical Timeline Projection Visualizer */}
      {subtasks.length > 0 && (
        <div className="mb-8 bg-white/2 p-6 rounded-xl border border-white/5 relative overflow-hidden h-48 flex flex-col justify-between">
          <div className="absolute inset-0 pointer-events-none opacity-5" style={{ backgroundImage: "radial-gradient(#ffffff 1px, transparent 1px)", backgroundSize: "16px 16px" }} />
          <div className="relative z-10 flex justify-between items-center">
            <h4 className="font-mono text-[9px] text-gray-400 uppercase tracking-widest">Timeline Projection</h4>
            <div className="flex gap-4 font-mono text-[8px] tracking-wide text-gray-500">
              <div className="flex items-center gap-1"><div className="w-1.5 h-1.5 rounded-full bg-[#FF453A]" />CRITICAL</div>
              <div className="flex items-center gap-1"><div className="w-1.5 h-1.5 rounded-full bg-amber-400" />HIGH</div>
              <div className="flex items-center gap-1"><div className="w-1.5 h-1.5 rounded-full bg-blue-500" />MEDIUM</div>
            </div>
          </div>
          <div className="relative z-10 flex items-end h-28 gap-2 pb-2">
            {subtasks.map((s) => {
              const weight = Math.min(100, Math.max(15, s.durationMinutes * 1.5));
              const barColor = 
                s.priority === 'CRITICAL' ? 'bg-[#FF453A] shadow-[0_-2px_10px_rgba(255,69,58,0.4)]' :
                s.priority === 'HIGH' ? 'bg-amber-400 shadow-[0_-2px_10px_rgba(245,158,11,0.25)]' :
                'bg-blue-500';
                
              return (
                <div 
                  key={s.id} 
                  className={`w-full ${barColor} rounded-t-sm transition-all duration-300`} 
                  style={{ height: `${weight}%` }}
                  title={`${s.title} (${s.durationMinutes}m)`}
                />
              );
            })}
          </div>
        </div>
      )}

      {/* Action Buttons */}
      <div className="flex justify-between items-center mt-6">
        <button 
          onClick={handleAdd}
          className="btn btn-secondary py-2.5 px-4 text-[10px]"
        >
          <Plus className="w-4 h-4" /> Add Subtask
        </button>

        <button 
          onClick={handlePushToCalendar}
          disabled={subtasks.length === 0 || isProcessing}
          className="btn btn-primary py-3 px-6 text-[11px] flex items-center gap-2"
        >
          <Play className="w-4 h-4 fill-white" />
          {isProcessing ? 'Processing...' : 'Lock plan & push to Calendar'}
        </button>
      </div>
    </div>
  );
}
