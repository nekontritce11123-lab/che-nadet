# Источники и решения

Проверено 26 сентября 2026. Ссылки — первичные источники. Формулы ощущаемой температуры не являются индивидуальной физиологической моделью.

| Источник | Что использовано |
|---|---|
| https://open-meteo.com/en/docs | Apparent temperature уже объединяет ветер, влажность и солнечное излучение; current — модельные значения. Единицы, interval, Unix time, WMO weather codes. |
| https://open-meteo.com/en/docs/geocoding-api | Поиск городов, country/admin1, GeoNames, координаты. |
| https://open-meteo.com/en/terms | Бесплатный API: некоммерческое использование, лимиты <10000/сутки, 5000/час, 600/мин, нет SLA; атрибуция CC BY 4.0. |
| https://api.met.no/ | Рассмотренная альтернатива. Open-Meteo выбран за единообразный набор текущих/часовых показателей и UV без ключа. |
| https://www.weather.gov/safety/cold-wind-chill-chart | Wind chill для T ≤ 10°C, ветра > 3 mph; RH не добавлять как отдельный «штраф холода». Холодный ветер не доказывает гололёд на поверхности выше 0°C. |
| https://www.weather.gov/media/epz/wxcalc/heatIndex.pdf | Коэффициенты Rothfusz heat index. Использовать только в обозначенной в коде жаркой/влажной области, не экстраполировать на холод. |
| https://www.weather.gov/ama/heatindex | В жаре влажность затрудняет охлаждение испарением. Индекс не учитывает все индивидуальные условия. |
| https://www.weather.gov/safety/cold-during | Съёмные слои, сухая одежда, ограничение пребывания снаружи. |
| https://www.weather.gov/safety/lightning-outdoors | При грозе приоритет надёжному укрытию, а не зонту. |
| https://www.who.int/news-room/questions-and-answers/item/radiation-the-ultraviolet-(uv)-index | Защита от солнца при UVI ≥ 3, повышенная осторожность при ≥ 8. Ночь и облака не подменяют измерение UV. |
| https://developer.android.com/topic/architecture/recommendations | ViewModel, Flow, разделение UI/data, lifecycle-aware collection. |
| https://developer.android.com/develop/sensors-and-location/location/permissions/runtime | Foreground / coarse location; возможность ручного выбора без разрешения. |
| https://developer.android.com/build/releases/agp-8-13-0-release-notes | AGP 8.13.2 совместим с Kotlin 2.3, Gradle 8.13, JDK 17, API 36. |
| https://developer.android.com/build/releases/agp-9-3-0-release-notes | Более новая линия AGP существует; намеренно не вводится новый DSL в небольшой v1. |
| https://kotlinlang.org/docs/releases.html | Kotlin 2.3.21 — фиксированная стабильная версия. |
| https://developer.android.com/jetpack/androidx/releases/compose | Compose / Material 3 — фиксированные стабильные зависимости, не динамические latest. |
| https://gradle.org/release-checksums/ | SHA-256 Gradle 8.13 binary ZIP: 20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78. |

## Научный факт ≠ продуктовый порог

Одежда, ±2°C чувствительности, поправки активности, длительность прогулки, границы одежды/ветра/осадков и 30m/6h/24h свежести — наши инженерные эвристики. Они описаны в коде и проверяются на непротиворечивость; исследования на людях не проводились. Приложение не заменяет официальные штормовые предупреждения или медицинскую консультацию. Предупреждения не зависят от желания пользователя одеться легче.

Текущие осадки за 15 минут и прогнозные за час не смешиваются: mapper нормирует current через interval. Вероятность осадков не превращается в интенсивность. Forecast-only защита подаётся как «взять», а не как утверждение, что сейчас идёт дождь.

## Сборочный процесс

https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle — закреплённый setup-gradle v4.4.2 SHA и сохранение артефактов.
https://github.com/ReactiveCircus/android-emulator-runner — API-level, x86_64, KVM и script для instrumentation.
https://developer.android.com/develop/ui/compose/testing/apis — semantics-based проверки UI. Workflow написан, но здесь не запускался.
