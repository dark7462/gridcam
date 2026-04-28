# GridCam Landing Page (React + Tailwind + shadcn/ui)

Landing page for GridCam built with:
- React + Vite
- Tailwind CSS v4
- shadcn/ui-style component structure (`components.json`, `src/components/ui/*`)

## Run locally

```bash
cd Ui
npm install
npm run dev
```

Then open the local URL shown in terminal (default `http://localhost:5173`).

## Build

```bash
cd Ui
npm run build
```

## Key structure

- `src/App.jsx` — landing page sections, content, and smooth-scroll anchors
- `src/components/ui/` — shadcn-style UI primitives (button, card, badge)
- `src/index.css` — Tailwind setup, theme tokens, motion utilities
- `public/images/branding/gridcam-logo.png` — logo asset
- `public/images/compositions/` — composition sample images (rule of thirds, golden spiral, leading lines, symmetry)
