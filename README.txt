СБОРКА ЧЕРЕЗ GITHUB (без установки чего-либо):
1. Создай новый репозиторий на github.com.
2. Загрузи в него СОДЕРЖИМОЕ этой папки (включая скрытую папку .github), не сам zip.
3. Открой вкладку Actions -> Build mod -> дождись зелёной галочки (3-10 минут).
4. Внизу запуска, в разделе Artifacts, скачай autobuyer-jar -> autobuyer-1.0.jar.
5. Положи jar в .minecraft/mods и запускай клиент Forge 1.16.5. Клавиша R - вкл/выкл.

СБОРКА НА СВОЁМ КОМПЬЮТЕРЕ (нужны JDK 17 для запуска Gradle и интернет; сам мод компилируется Java 8-toolchain):
  Windows:     gradlew.bat build
  Linux/Mac:   sh gradlew build
Первый запуск скачает Gradle 7.6.4 и зависимости, это долго. Результат: build/libs/autobuyer-1.0.jar

Настройки (слоты, количество кликов, задержка) - в начале AutoBuyer.java.
