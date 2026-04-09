import { createTheme, responsiveFontSizes } from '@mui/material/styles';

// Color palette extracted from existing App.css
// Primary dark: #1a1a2e (body background)
// Secondary dark: #16213e (card background, header gradient start)
// Tertiary dark: #0f3460 (borders, header gradient end)
// Accent: #e94560 (headings, errors, hover)
// Text primary: #e0e0e0
// Text secondary: #a0a8b8
// Link color: #4fc3f7

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#e94560',
      light: '#ff7b8a',
      dark: '#b01035',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#4fc3f7',
      light: '#8bf6ff',
      dark: '#0093c4',
      contrastText: '#000000',
    },
    background: {
      default: '#1a1a2e',
      paper: '#16213e',
    },
    text: {
      primary: '#e0e0e0',
      secondary: '#a0a8b8',
    },
    error: {
      main: '#e94560',
    },
    warning: {
      main: '#ffa726',
    },
    success: {
      main: '#66bb6a',
    },
    info: {
      main: '#4fc3f7',
    },
    divider: '#0f3460',
  },
  typography: {
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      'Oxygen',
      'Ubuntu',
      'Cantarell',
      '"Fira Sans"',
      '"Droid Sans"',
      '"Helvetica Neue"',
      'sans-serif',
    ].join(','),
    h1: {
      fontSize: '2.5rem',
      fontWeight: 600,
    },
    h2: {
      fontSize: '2rem',
      fontWeight: 600,
    },
    h3: {
      fontSize: '1.5rem',
      fontWeight: 600,
    },
    h4: {
      fontSize: '1.25rem',
      fontWeight: 600,
    },
    h5: {
      fontSize: '1.1rem',
      fontWeight: 600,
    },
    h6: {
      fontSize: '1rem',
      fontWeight: 600,
    },
  },
  shape: {
    borderRadius: 12,
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          scrollbarColor: '#0f3460 #1a1a2e',
          '&::-webkit-scrollbar': {
            width: '8px',
            height: '8px',
          },
          '&::-webkit-scrollbar-track': {
            background: '#1a1a2e',
          },
          '&::-webkit-scrollbar-thumb': {
            background: '#0f3460',
            borderRadius: '4px',
          },
          '&::-webkit-scrollbar-thumb:hover': {
            background: '#16213e',
          },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid #0f3460',
          boxShadow: '0 4px 16px rgba(0, 0, 0, 0.3)',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 500,
          '&:focus-visible': {
            outline: '2px solid #4fc3f7',
            outlineOffset: '2px',
          },
        },
        contained: {
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.3)',
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundImage: 'linear-gradient(135deg, #16213e 0%, #0f3460 100%)',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.4)',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: '#16213e',
          borderRight: '1px solid #0f3460',
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          '&.Mui-selected': {
            backgroundColor: 'rgba(233, 69, 96, 0.15)',
            borderRight: '3px solid #e94560',
            '&:hover': {
              backgroundColor: 'rgba(233, 69, 96, 0.25)',
            },
          },
          '&:hover': {
            backgroundColor: 'rgba(79, 195, 247, 0.1)',
          },
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            '& fieldset': {
              borderColor: '#0f3460',
            },
            '&:hover fieldset': {
              borderColor: '#4fc3f7',
            },
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottom: '1px solid #0f3460',
        },
        head: {
          backgroundColor: '#0f3460',
          fontWeight: 600,
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 500,
        },
      },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: {
          backgroundColor: '#0f3460',
          border: '1px solid #16213e',
        },
      },
    },
    // Ensure icon buttons meet 44x44px minimum touch target
    MuiIconButton: {
      styleOverrides: {
        root: {
          '&:focus-visible': {
            outline: '2px solid #4fc3f7',
            outlineOffset: '2px',
          },
        },
        sizeSmall: {
          padding: 8, // 24px icon + 2*8px padding = 40px (close to 44px)
          '@media (pointer: coarse)': {
            padding: 10, // 44px on touch devices
          },
        },
      },
    },
  },
});

// Apply responsive font sizes for better scaling across breakpoints
const responsiveTheme = responsiveFontSizes(theme);

export default responsiveTheme;
