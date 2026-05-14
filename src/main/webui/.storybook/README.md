# Storybook for Frodo UI

Component library documentation and development environment for the Frodo React frontend.

## Quick Start

### Using Gradle (Recommended)

From project root:

```bash
./gradlew storybook              # Start dev server on port 6006
./gradlew buildStorybook         # Build static site
```

### Using npm directly

From `src/main/webui`:

```bash
npm run storybook                # Start dev server
npm run build-storybook          # Build static site
```

Storybook will start on http://localhost:6006

## Available Commands

| Command | Description |
|---------|-------------|
| `./gradlew storybook` | Start Storybook dev server (uses Quinoa's Node.js) |
| `./gradlew buildStorybook` | Build static Storybook site to `storybook-static/` |
| `npm run storybook` | Start dev server (requires Node.js in PATH) |
| `npm run build-storybook` | Build static site (requires Node.js in PATH) |

## Writing Stories

Stories are co-located with components in `src/components/`. Each component can have a corresponding `.stories.js` file.

### Example Story Structure

```javascript
import MyComponent from './MyComponent';

export default {
  title: 'Category/MyComponent',
  component: MyComponent,
  parameters: {
    layout: 'centered', // or 'padded', 'fullscreen'
  },
  tags: ['autodocs'], // Auto-generate docs from JSDoc
  argTypes: {
    propName: {
      control: 'text', // or 'boolean', 'select', 'number', etc.
      description: 'Prop description',
    },
  },
};

export const Default = {
  args: {
    propName: 'value',
  },
};

export const Variant = {
  args: {
    propName: 'different value',
  },
};
```

### Story Naming Convention

- File: `ComponentName.stories.js` (co-located with `ComponentName.jsx`)
- Title: `Category/ComponentName` (e.g., `Common/StatusChip`)
- Exports: Named exports for each variant (e.g., `Default`, `Connected`, `Loading`)

## Theme Integration

All stories are automatically wrapped with:
- MUI `ThemeProvider` (Frodo dark theme)
- `CssBaseline` (global styles)

No need to manually wrap components in theme providers.

## JSX in .js Files

The codebase uses `.js` extensions for JSX files (legacy CRA pattern). Storybook is configured to handle this via `.storybook/vite.config.js` with the same `transformWithOxc` plugin used in the main app.

## Addons Included

- **Essentials** - Controls, Actions, Viewport, Backgrounds, Toolbars, Measure, Outline
- **Interactions** - Test user interactions
- **Links** - Navigate between stories
- **A11y** - Accessibility testing

## Component Coverage

### Common Components (✓ Complete)

- `StatusChip` - Connection status display
- `LoadingSpinner` - Loading states
- `ErrorDisplay` - Error messages
- `EmptyState` - Empty data states
- `PageHeader` - Page titles with actions
- `ConfirmDialog` - Confirmation dialogs
- `NotificationSnackbar` - Toast notifications

### Future Coverage

Add stories for:
- Dashboard components
- Device components
- Metrics components
- Grafana components
- Layout components

## Building for Production

```bash
npm run build-storybook
```

Output: `storybook-static/` (gitignored)

Can be deployed to static hosting (GitHub Pages, Netlify, etc.)

## Troubleshooting

### Port 6006 already in use

Change port in `package.json`:
```json
"storybook": "storybook dev -p 6007"
```

### Stories not loading

Check that story files match pattern in `.storybook/main.js`:
```javascript
stories: ['../src/**/*.stories.@(js|jsx|ts|tsx)']
```

### Theme not applied

Verify `.storybook/preview.js` has `ThemeProvider` decorator.

### JSX syntax errors in .js files

Ensure `.storybook/vite.config.js` includes the `treat-js-files-as-jsx` plugin.

## Resources

- [Storybook Documentation](https://storybook.js.org/docs)
- [MUI Component Library](https://mui.com/material-ui/)
- [Frodo Theme](../src/theme/index.js)
