import React, { useEffect, useMemo } from 'react';
import { BarChart3, Brain, Cpu, Zap } from 'lucide-react';
import {
  Legend, PolarAngleAxis, PolarGrid, PolarRadiusAxis,
  Radar, RadarChart, ResponsiveContainer, Tooltip,
} from 'recharts';
import { C, FONT_MONO } from '../constants.js';
import { Spinner, Section, LiveBtn } from './shared.jsx';
import { fmt } from '../utils/format.js';
import { fetchMLLatest } from '../utils/api.js';

const MODEL_COLORS = [C.teal, C.blue, C.purple, C.orange];

export function MLTab({ ml, loading, runTraining, onMLData }) {
  // On mount: load the most recent training run from the backend
  useEffect(() => {
    if (!ml && !loading) {
      fetchMLLatest()
        .then((data) => { if (data) onMLData(data); })
        .catch(() => {}); // silently ignore — no training run yet
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const radarData = useMemo(() => {
    if (!ml?.models) return [];
    const metrics = ['acc', 'prec', 'rec', 'f1', 'auc'];
    const labels = { acc: 'Accuracy', prec: 'Precision', rec: 'Recall', f1: 'F1', auc: 'ROC-AUC' };
    return metrics.map((m) => {
      const row = { metric: labels[m] };
      for (const model of ml.models) {
        row[model.name] = Number((Number(model[m]) * 100).toFixed(1));
      }
      return row;
    });
  }, [ml]);

  const featureColor = (dir) => {
    const d = (dir || '').toLowerCase();
    if (d === 'bullish') return C.teal;
    if (d === 'bearish') return C.red;
    return C.yellow;
  };

  return (
    <div>
      <Section
        title="Train live models"
        subtitle="Trains XGBoost, LightGBM, RandomForest and GradientBoosting on real OHLCV data from NSE. Features: RSI, MACD, EMA ratios, volume, patterns. Label: 20-day forward return > 3%."
        icon={<Cpu size={18} />}
        right={
          <LiveBtn
            icon={<Zap size={15} />}
            label="Train Live Models"
            loading={loading}
            onClick={runTraining}
          />
        }
      >
        {!ml && !loading && (
          <div style={{ color: C.muted, fontSize: 13 }}>
            No training run yet. Click <b>Train Live Models</b> to kick one off.
          </div>
        )}
        {loading && (
          <div style={{ color: C.sub, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
            <Spinner /> Training on real market data — cross-validating with TimeSeriesSplit…
          </div>
        )}
      </Section>

      {ml && (
        <>
          {/* Model cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, marginBottom: 18 }}>
            {ml.models?.map((model, idx) => (
              <div
                key={model.name}
                style={{
                  background: C.card,
                  border: `1px solid ${model.best ? C.teal : C.border}`,
                  borderRadius: 14,
                  padding: '1rem',
                  position: 'relative',
                }}
              >
                {model.best && (
                  <span
                    style={{
                      position: 'absolute',
                      top: 10,
                      right: 12,
                      padding: '2px 8px',
                      background: 'rgba(0,212,168,0.15)',
                      color: C.teal,
                      borderRadius: 999,
                      fontSize: 10,
                      fontWeight: 700,
                      letterSpacing: 0.6,
                      textTransform: 'uppercase',
                    }}
                  >
                    Best
                  </span>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <div
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      background: MODEL_COLORS[idx % MODEL_COLORS.length],
                    }}
                  />
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{model.name}</div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
                  {['acc', 'prec', 'rec', 'f1', 'auc'].map((k) => (
                    <div
                      key={k}
                      style={{ background: C.dark, border: `1px solid ${C.border}`, borderRadius: 8, padding: '6px 8px' }}
                    >
                      <div style={{ fontSize: 9.5, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.6 }}>
                        {k}
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 700, color: C.text, fontFamily: FONT_MONO }}>
                        {fmt(Number(model[k]) * 100, 1)}%
                      </div>
                    </div>
                  ))}
                </div>
                <div style={{ marginTop: 8, fontSize: 11, color: C.muted }}>
                  Train time: {fmt(model.train_time_s)}s
                </div>
              </div>
            ))}
          </div>

          {/* Model insights */}
          {ml.analysis_note && (
            <Section title="Model insights" icon={<Brain size={18} />}>
              <p style={{ margin: 0, color: C.sub, fontSize: 13, lineHeight: 1.65 }}>{ml.analysis_note}</p>
              <div style={{ display: 'flex', gap: 16, marginTop: 10, color: C.muted, fontSize: 11 }}>
                <span>Dataset: {fmt(ml.dataset_size, 0)} rows</span>
                <span>CV folds: {ml.cv_folds}</span>
                <span>Threshold: {fmt(ml.best_threshold, 2)}</span>
                <span>Window: {ml.training_period}</span>
                {ml.trained_at && <span>Trained: {new Date(ml.trained_at).toLocaleString()}</span>}
              </div>
            </Section>
          )}

          {/* Radar chart */}
          <Section title="Model comparison" subtitle="5 metrics, normalised 0-100" icon={<BarChart3 size={18} />}>
            <div style={{ width: '100%', height: 300 }}>
              <ResponsiveContainer>
                <RadarChart data={radarData}>
                  <PolarGrid stroke={C.border} />
                  <PolarAngleAxis dataKey="metric" stroke={C.sub} fontSize={11} />
                  <PolarRadiusAxis stroke={C.muted} fontSize={10} domain={[0, 100]} />
                  {ml.models?.map((model, idx) => (
                    <Radar
                      key={model.name}
                      name={model.name}
                      dataKey={model.name}
                      stroke={MODEL_COLORS[idx % MODEL_COLORS.length]}
                      fill={MODEL_COLORS[idx % MODEL_COLORS.length]}
                      fillOpacity={0.15}
                      strokeWidth={2}
                    />
                  ))}
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Tooltip contentStyle={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 8, fontSize: 12 }} />
                </RadarChart>
              </ResponsiveContainer>
            </div>
          </Section>

          {/* Feature importance */}
          {ml.features && (
            <Section title="Feature importance" subtitle="Direction-coloured · from best model" icon={<BarChart3 size={18} />}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {ml.features.map((f) => {
                  const color = featureColor(f.direction);
                  const pct = Math.max(0, Math.min(100, Number(f.importance) || 0));
                  return (
                    <div key={f.name} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{ width: 180, fontSize: 12, color: C.sub }}>{f.name}</div>
                      <div style={{ flex: 1, height: 10, background: C.dark, borderRadius: 999, overflow: 'hidden' }}>
                        <div style={{ width: `${pct}%`, height: '100%', background: color }} />
                      </div>
                      <div style={{ width: 60, textAlign: 'right', fontSize: 11, color: C.muted, fontFamily: FONT_MONO }}>
                        {fmt(pct, 1)}%
                      </div>
                    </div>
                  );
                })}
              </div>
            </Section>
          )}
        </>
      )}
    </div>
  );
}
