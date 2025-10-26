document.addEventListener('DOMContentLoaded', function() {
    // Get DOM elements
    const currentUrlElement = document.getElementById('current-url');
    const pageTitleElement = document.getElementById('page-title');
    const highlightBtn = document.getElementById('highlight-btn');
    const countWordsBtn = document.getElementById('count-words-btn');
    const clearBtn = document.getElementById('clear-btn');
    const resultElement = document.getElementById('result');
    const clickCountElement = document.getElementById('click-count');

    // Load click count from storage
    chrome.storage.local.get(['clickCount'], function(result) {
        clickCountElement.textContent = result.clickCount || 0;
    });

    // Get current tab info
    chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
        const currentTab = tabs[0];
        currentUrlElement.textContent = currentTab.url;
        pageTitleElement.textContent = currentTab.title;
    });

    // Highlight button click handler
    highlightBtn.addEventListener('click', function() {
        chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
            chrome.tabs.sendMessage(tabs[0].id, {action: 'highlight'}, function(response) {
                if (chrome.runtime.lastError) {
                    showResult('Error', 'Could not highlight page. Make sure the content script is loaded.');
                } else {
                    showResult('Success', 'Page highlighted successfully!');
                }
            });
        });
        updateClickCount();
    });

    // Count words button click handler
    countWordsBtn.addEventListener('click', function() {
        chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
            chrome.tabs.sendMessage(tabs[0].id, {action: 'countWords'}, function(response) {
                if (chrome.runtime.lastError) {
                    showResult('Error', 'Could not count words. Make sure the content script is loaded.');
                } else if (response) {
                    showResult('Word Count', `This page contains ${response.wordCount} words.`);
                }
            });
        });
        updateClickCount();
    });

    // Clear button click handler
    clearBtn.addEventListener('click', function() {
        chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
            chrome.tabs.sendMessage(tabs[0].id, {action: 'clear'}, function(response) {
                if (chrome.runtime.lastError) {
                    showResult('Error', 'Could not clear highlights.');
                } else {
                    showResult('Success', 'Highlights cleared!');
                }
            });
        });
        updateClickCount();
    });

    // Function to show result
    function showResult(title, message) {
        resultElement.innerHTML = `
            <h3>${title}</h3>
            <p>${message}</p>
        `;
        resultElement.classList.add('show');
        
        // Hide result after 3 seconds
        setTimeout(() => {
            resultElement.classList.remove('show');
        }, 3000);
    }

    // Function to update click count
    function updateClickCount() {
        chrome.storage.local.get(['clickCount'], function(result) {
            const newCount = (result.clickCount || 0) + 1;
            chrome.storage.local.set({clickCount: newCount}, function() {
                clickCountElement.textContent = newCount;
            });
        });
    }
});