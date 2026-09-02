<div align="center">
  <!-- Si quieres que se vea el logo aquí, añade la imagen y la ruta -->
  <h1>📱 R2Pilot - Frontend (Android App)</h1>
  <p><strong>Aplicación nativa de Android para el control y monitorización de robots basados en ROS 2.</strong></p>
</div>

## 🎓 Contexto Académico

Este repositorio contiene el código del cliente móvil (Frontend) correspondiente al **Trabajo Fin de Grado (TFG)**:

* **Título:** *Desarrollo de una aplicación móvil para el control y monitorización de robots basados en ROS 2*
* **Autor:** Enrique Gómez Pacheco
* **Tutor:** Juan José Ramos Muñoz
* **Universidad:** Universidad de Granada (UGR) - ETSIIT / TSTC (2026)

📄 **[Consultar Memoria del TFG (PDF)](docs/Memoria_TFG_R2Pilot_Enrique_Gomez.pdf)**
🔗 **[Ver Repositorio del Backend (Servidor)](https://github.com/enriquegmez/TFG_R2Pilot-Backend.git)**
📚 **[Ver Documentación de Código (Doxygen)](https://enriquegmez.github.io/TFG_R2Pilot-Frontend/doxygen/html/index.html)**

---

## 📝 Descripción

R2Pilot es una aplicación móvil desarrollada de forma nativa en **Kotlin** utilizando **Jetpack Compose** para el diseño de la interfaz gráfica. Su propósito es proporcionar a los operadores una herramienta portátil e intuitiva para interactuar con robots móviles, evitando la necesidad de depender de ordenadores o terminales de comandos.

Se conecta con el robot mediante una arquitectura cliente-servidor basada en **WebSockets**, intercambiando información en tiempo real mediante un protocolo propio.

---

## ⚙️ Requisitos Previos

* **Entorno de Desarrollo:** Android Studio.
* **Sistema Operativo (Dispositivo):** Android 8.0 (Oreo) o superior (API 26+).
* **Red:** Conexión a la misma red Wi-Fi local que el servidor Backend.

---

## 🚀 Instalación y Despliegue

**1. Clonar el repositorio:**

```bash
git clone https://github.com/enriquegmez/TFG_R2Pilot-Frontend.git

```

**2. Abrir en Android Studio:**

* Abra **Android Studio**, seleccione `Open` y navegue hasta la carpeta clonada.
* Espere a que el sistema sincronice el proyecto y descargue las dependencias de Gradle.

**3. Configurar la IP del servidor (Opcional):**

La aplicación solicitará la dirección IP del servidor al iniciar. Si utiliza siempre el mismo robot y quiere establecer una IP predeterminada, puede modificar la constante `DEFAULT_SERVER_IP` en el archivo:

`app/src/main/java/com/enrique/r2pilot/utils/Constants.kt`

**4. Compilar y ejecutar:**

* Conecte un dispositivo Android, físico o emulador.
* Seleccione el dispositivo en la barra superior de Android Studio y pulsa **Run (Shift + F10)**.

> **Nota:** Si no desea compilar el código desde cero, puede descargar el instalador directamente desde la pestaña **Releases** de este repositorio (archivo `.apk`).
