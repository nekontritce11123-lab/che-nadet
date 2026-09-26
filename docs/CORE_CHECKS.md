# Executed core checks

Groups passed: 45
Assertions executed: 4186

- PASS provider apparent temperature is not counted twice
- PASS 10 C calm wind and rain have different protective layers
- PASS 14.9 and 15 C do not jump from jacket to t-shirt
- PASS personalization changes clothes but not thermometer
- PASS active movement lowers warmth; long exposure raises it
- PASS strong wind with rain never recommends an umbrella
- PASS unknown wind chooses raincoat rather than umbrella
- PASS thunderstorm warning outranks wardrobe
- PASS freezing rain differs from ordinary rain
- PASS cold wet conditions warn about possible slippery surfaces
- PASS wind chill below zero alone does not imply icy roads
- PASS heat safety is independent from personal sensitivity
- PASS severe cold uses thermal layers hat and gloves
- PASS daytime UV protection from 3 including cloudy weather
- PASS night never describes current sunshine or high current UV
- PASS forecast rain packs protection without claiming rain now
- PASS rain outside activity window does not affect this outing
- PASS fast cooling prompts an extra removable layer
- PASS missing apparent falls back to domain-limited wind chill
- PASS calm missing apparent does not invent cold humidity penalty
- PASS missing apparent has bounded heat index fallback
- PASS nonfinite or out of range optional data is not treated as valid
- PASS negative precipitation never creates a rain recommendation
- PASS warmth demand changes continuously across temperature grid
- PASS every profile and weather combination has sane unique layers
- PASS cache freshness has explicit current stale expired and hidden states
- PASS recent download of old model data is still stale
- PASS future timestamp from wrong clock cannot be fresh
- PASS cache never leaks weather from a previous city
- PASS late network request cannot replace a newer request
- PASS permission states distinguish first prompt refusal and settings recovery
- PASS all garments accessories reasons and warnings have concise Russian text
- PASS missing metrics are not rendered as a fabricated zero
- PASS weather URL uses SI unix timestamps bounded hours and no API key
- PASS URL coordinates and cache identity ignore device locale
- PASS manual city input is encoded not interpreted as URL parameters
- PASS decoder normalizes 15 minute accumulation into equivalent mm per hour
- PASS decoder never double-applies timezone offset to Unix time
- PASS missing interval does not assume an accumulation period
- PASS zero missing and invalid observations remain distinct
- PASS malformed mandatory current temperature is rejected
- PASS unexpected API unit never silently gives wrong advice
- PASS short hourly arrays preserve nulls and do not crash
- PASS empty city response is empty not a fake default town
- PASS forecast window sorts deduplicates and discards past data

Exit: 0
