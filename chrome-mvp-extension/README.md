# MVP Chrome Extension

A simple MVP (Minimum Viable Product) Chrome extension that demonstrates basic Chrome extension functionality.

## Features

- **Popup Interface**: Clean and user-friendly popup with current page information
- **Page Highlighting**: Highlight all paragraphs and headings on the current page
- **Word Count**: Count the total number of words on the current page
- **Click Tracking**: Track how many times you've used the extension
- **Context Menu**: Right-click on any page to access extension features
- **Persistent Storage**: Uses Chrome's storage API to save user data
- **Notifications**: Shows helpful notifications for user actions

## File Structure

```
chrome-mvp-extension/
├── manifest.json          # Extension manifest (configuration)
├── popup.html            # Popup interface HTML
├── css/
│   ├── popup.css         # Popup styling
│   └── content.css       # Content script styling
├── js/
│   ├── background.js     # Background service worker
│   ├── content.js        # Content script for page interaction
│   └── popup.js          # Popup script logic
└── icons/
    ├── icon16.png        # 16x16 icon
    ├── icon32.png        # 32x32 icon
    ├── icon48.png        # 48x48 icon
    └── icon128.png       # 128x128 icon
```

## Installation

### Development Mode

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable "Developer mode" (toggle in top right)
3. Click "Load unpacked"
4. Select the `chrome-mvp-extension` folder
5. The extension should now appear in your extensions list

### Usage

1. Click the extension icon in Chrome's toolbar
2. Use the buttons in the popup:
   - **Highlight Page**: Highlights all text elements on the current page
   - **Count Words**: Shows the total word count for the current page
   - **Clear**: Removes all highlights from the page
3. Right-click on any page to access extension features via context menu

## Permissions

- `activeTab`: Access to the currently active tab
- `storage`: Store extension settings and user data
- `notifications`: Show notifications to the user

## Development

This extension uses:
- **Manifest V3**: Latest Chrome extension manifest version
- **Service Worker**: Background script using service worker pattern
- **Content Scripts**: Injected into web pages
- **Chrome APIs**: storage, tabs, notifications, contextMenus

## Testing

1. Install the extension in development mode
2. Navigate to any website
3. Click the extension icon to open the popup
4. Test all features:
   - Check if current URL and title display correctly
   - Test highlight functionality
   - Test word count feature
   - Test clear functionality
   - Check click counter increment

## Building for Production

To create a production-ready extension:

1. Test thoroughly in development mode
2. Create a `.zip` file of the extension folder
3. Upload to Chrome Web Store (requires developer account)

## Troubleshooting

- **Extension not working**: Check Chrome console for errors
- **Content script not loading**: Refresh the page after installing
- **Storage issues**: Clear extension data in Chrome settings

## License

This is a demonstration project for educational purposes.