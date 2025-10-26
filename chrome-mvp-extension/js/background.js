// Background script (Service Worker) for MVP Chrome Extension

// Install event - fired when the extension is installed or updated
chrome.runtime.onInstalled.addListener(function(details) {
    console.log('MVP Chrome Extension installed/updated');
    
    // Initialize storage
    chrome.storage.local.set({
        clickCount: 0,
        installDate: new Date().toISOString()
    });
    
    // Show welcome notification
    if (details.reason === 'install') {
        chrome.notifications.create({
            type: 'basic',
            iconUrl: 'icons/icon48.png',
            title: 'MVP Extension Installed!',
            message: 'Your MVP Chrome Extension is ready to use. Click the extension icon to get started!'
        });
    }
});

// Startup event - fired when the browser starts
chrome.runtime.onStartup.addListener(function() {
    console.log('MVP Chrome Extension - Browser started');
});

// Message handler for background script
chrome.runtime.onMessage.addListener(function(request, sender, sendResponse) {
    console.log('Background script received message:', request);
    
    switch (request.action) {
        case 'getTabInfo':
            chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
                sendResponse({tab: tabs[0]});
            });
            return true; // Keep message channel open for async response
            
        case 'logActivity':
            console.log('Activity logged:', request.data);
            // Store activity in local storage
            chrome.storage.local.get(['activities'], function(result) {
                const activities = result.activities || [];
                activities.push({
                    action: request.data.action,
                    timestamp: new Date().toISOString(),
                    url: sender.tab ? sender.tab.url : 'background'
                });
                // Keep only last 50 activities
                if (activities.length > 50) {
                    activities.shift();
                }
                chrome.storage.local.set({activities: activities});
            });
            sendResponse({status: 'logged'});
            break;
            
        default:
            sendResponse({error: 'Unknown action'});
    }
});

// Context menu setup (optional - can be extended)
chrome.runtime.onInstalled.addListener(function() {
    // Create context menu items
    chrome.contextMenus.create({
        id: 'mvp-highlight',
        title: 'Highlight with MVP Extension',
        contexts: ['page']
    });
    
    chrome.contextMenus.create({
        id: 'mvp-count-words',
        title: 'Count words on page',
        contexts: ['page']
    });
});

// Context menu click handler
chrome.contextMenus.onClicked.addListener(function(info, tab) {
    switch (info.menuItemId) {
        case 'mvp-highlight':
            chrome.tabs.sendMessage(tab.id, {action: 'highlight'});
            break;
        case 'mvp-count-words':
            chrome.tabs.sendMessage(tab.id, {action: 'countWords'}, function(response) {
                if (response) {
                    chrome.notifications.create({
                        type: 'basic',
                        iconUrl: 'icons/icon48.png',
                        title: 'Word Count',
                        message: `This page contains ${response.wordCount} words.`
                    });
                }
            });
            break;
    }
});

// Tab update listener - can be used to track page changes
chrome.tabs.onUpdated.addListener(function(tabId, changeInfo, tab) {
    if (changeInfo.status === 'complete') {
        console.log('Page loaded:', tab.url);
    }
});

// Alarm/schedule functionality (optional)
chrome.alarms.onAlarm.addListener(function(alarm) {
    console.log('Alarm fired:', alarm.name);
});

// Keep service worker alive
chrome.runtime.onConnect.addListener(function(port) {
    console.log('Port connected:', port.name);
    
    if (port.name === 'keepAlive') {
        port.onMessage.addListener(function(msg) {
            if (msg.keepAlive) {
                // Respond to keep alive message
                port.postMessage({status: 'alive'});
            }
        });
    }
});

console.log('MVP Chrome Extension - Background script loaded');