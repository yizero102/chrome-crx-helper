// Content script for MVP Chrome Extension

// Listen for messages from popup
chrome.runtime.onMessage.addListener(function(request, sender, sendResponse) {
    switch (request.action) {
        case 'highlight':
            highlightPage();
            sendResponse({status: 'success'});
            break;
        case 'countWords':
            const wordCount = countWords();
            sendResponse({wordCount: wordCount});
            break;
        case 'clear':
            clearHighlights();
            sendResponse({status: 'success'});
            break;
    }
});

// Function to highlight the page
function highlightPage() {
    // Remove existing highlights first
    clearHighlights();
    
    // Add highlight to all paragraphs
    const paragraphs = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, td');
    paragraphs.forEach((element, index) => {
        setTimeout(() => {
            element.style.backgroundColor = '#ffeb3b';
            element.style.transition = 'background-color 0.3s ease';
            element.classList.add('mvp-highlight');
        }, index * 50);
    });
    
    // Add some styling to make highlights more visible
    const style = document.createElement('style');
    style.textContent = `
        .mvp-highlight {
            border-left: 3px solid #ff9800 !important;
            padding-left: 10px !important;
            margin-left: -13px !important;
        }
    `;
    style.id = 'mvp-highlight-style';
    document.head.appendChild(style);
}

// Function to count words on the page
function countWords() {
    // Get all text content from the page
    const textContent = document.body.innerText;
    
    // Remove extra whitespace and count words
    const words = textContent.trim().split(/\s+/);
    
    // Filter out empty strings
    const validWords = words.filter(word => word.length > 0);
    
    return validWords.length;
}

// Function to clear all highlights
function clearHighlights() {
    const highlightedElements = document.querySelectorAll('.mvp-highlight');
    highlightedElements.forEach(element => {
        element.style.backgroundColor = '';
        element.style.borderLeft = '';
        element.style.paddingLeft = '';
        element.style.marginLeft = '';
        element.classList.remove('mvp-highlight');
    });
    
    // Remove the highlight style
    const highlightStyle = document.getElementById('mvp-highlight-style');
    if (highlightStyle) {
        highlightStyle.remove();
    }
}

// Add some basic styling for the content script
const contentStyle = document.createElement('style');
contentStyle.textContent = `
    .mvp-highlight {
        transition: all 0.3s ease;
    }
`;
document.head.appendChild(contentStyle);

// Log when content script is loaded
console.log('MVP Chrome Extension - Content script loaded');