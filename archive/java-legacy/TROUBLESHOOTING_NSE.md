# NSE API Troubleshooting Guide

## ✅ Recent Fixes Applied

### 1. **Added Session Management to ComprehensiveNseDataService**
   - NSE API requires session cookies to access data
   - Service now establishes session before API calls
   - Session cookies are cached and refreshed every 30 minutes

### 2. **Enhanced Parsing Logic**
   - Handles multiple NSE response structures:
     - `data` section (legacy)
     - `priceInfo` section (newer)
     - Root level fields
   - Checks for null nodes before parsing
   - Tries alternative field names if standard ones are missing

### 3. **Improved Error Handling & Logging**
   - Detailed logging of response structure
   - Logs available keys when parsing fails
   - Logs response body for debugging
   - Better error messages with context

## 🔍 How to Debug Issues

### Check Console Logs

When you call the API, look for these log messages:

1. **Session Establishment:**
   ```
   ComprehensiveNseDataService - NSE session established with cookies: X
   ```

2. **API Request:**
   ```
   ComprehensiveNseDataService - Fetching from: https://www.nseindia.com/api/quote-equity?symbol=IRFC
   ComprehensiveNseDataService - Response status: 200
   ```

3. **Response Structure:**
   ```
   ComprehensiveNseDataService - NSE Response structure for IRFC:
   ComprehensiveNseDataService - Root keys: [priceInfo, info, marketDepth, ...]
   ComprehensiveNseDataService - Using 'data' section for IRFC
   ```

4. **If Parsing Fails:**
   ```
   ComprehensiveNseDataService - ERROR: No valid data section found for IRFC
   ComprehensiveNseDataService - Available root keys: [...]
   ComprehensiveNseDataService - Full NSE response for IRFC (first 2000 chars): {...}
   ```

## 🐛 Common Issues & Solutions

### Issue: "No data section found in NSE API response"

**Possible Causes:**
1. **Session expired** - NSE requires valid session cookies
2. **Different response structure** - Some stocks return different formats
3. **Empty response** - NSE might return empty or error response

**Solution:**
- Check console logs for actual response structure
- Verify session is being established
- Check if NSE API is accessible

### Issue: HTTP 403 Forbidden

**Cause:** Session cookies expired or missing

**Solution:**
- Session is automatically refreshed every 30 minutes
- Service will retry with new session if 403 is detected

### Issue: Empty Response Body

**Cause:** NSE API might be blocking requests or symbol is invalid

**Solution:**
- Check if symbol is correct (e.g., "IRFC" not "IRFC.NS")
- Verify NSE API is accessible
- Check logs for response status code

## 📋 What to Check in Logs

When debugging, look for these in console output:

1. **Which service is being used?**
   - `McpDataService` or `ComprehensiveNseDataService`

2. **What's the response structure?**
   - `Root keys: [...]` - Shows what keys are in the response
   - `Using 'data' section` or `Using 'priceInfo' section` - Shows which structure was detected

3. **What's the actual response?**
   - `Response body preview: {...}` - Shows first 500 chars of response
   - `Full NSE response: {...}` - Shows first 2000 chars if error occurs

4. **Session status?**
   - `NSE session established with cookies: X` - Session is working
   - `Session expired, refreshing...` - Session expired and being refreshed

## 🔄 Testing Steps

1. **Restart the application** to ensure latest code is loaded:
   ```bash
   mvn clean compile
   mvn spring-boot:run
   ```

2. **Test the API:**
   ```bash
   curl http://localhost:8080/api/nse/stock/IRFC/comprehensive
   ```

3. **Check console logs** for:
   - Session establishment
   - API request details
   - Response structure
   - Any error messages

4. **If it fails**, copy the log output showing:
   - Root keys in response
   - Response body preview
   - Error messages

## 📝 Next Steps

If the issue persists:

1. **Share the console logs** showing:
   - Root keys in the response
   - Response body preview
   - Any error messages

2. **Check NSE API directly:**
   - Try accessing `https://www.nseindia.com/api/quote-equity?symbol=IRFC` in browser
   - Check if you get a response (may need to visit NSE homepage first)

3. **Verify symbol format:**
   - Some symbols might need different format
   - Check NSE website for correct symbol format

## 🔧 Code Changes Summary

### ComprehensiveNseDataService.java
- ✅ Added session management (`establishNseSession()`)
- ✅ Added session headers (`createNseHeaders()`)
- ✅ Enhanced parsing to handle multiple structures
- ✅ Added fallback field name checks
- ✅ Improved error logging

### McpDataService.java
- ✅ Enhanced parsing logic
- ✅ Better null checks
- ✅ More field name variations
- ✅ Improved error logging

Both services now:
- Establish NSE sessions automatically
- Handle multiple response structures
- Provide detailed logging for debugging
- Fallback to alternative field names

---

**Last Updated:** 2024-11-06
**Status:** ✅ Code compiled and ready to test

