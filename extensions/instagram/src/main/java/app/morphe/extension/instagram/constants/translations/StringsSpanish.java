/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.constants.translations;

public class StringsSpanish extends DefaultStrings {

    public StringsSpanish() {

        this.PIKO_SETTINGS_TITLE = "Configuración de Piko";

        this.CATEGORY_ADS = "Anuncios";
        this.DISABLE_ADS = "Desactivar anuncios";
        this.HIDE_SUGEESTED_CONTENT = "Ocultar contenido sugerido";
        this.HIDE_SUGEESTED_CONTENT_DESC = "Ocultar historias, reels y threads sugeridos (las publicaciones sugeridas seguirán apareciendo).";

        this.CATEGORY_DEV_OPTIONS = "Opciones de desarrollador";
        this.ENABLE_DEV_OPTIONS = "Activar opciones de desarrollador";
        this.ENABLE_DEV_OPTIONS_DESC = "Desbloquear opciones de desarrollador al mantener presionado el botón de inicio";
        this.DIRECTLY_OPEN_METACONFIG = "Abrir MetaConfig overrides directamente";
        this.DIRECTLY_OPEN_METACONFIG_DESC = "Abrir directamente MetaConfig overrides al mantener presionado el botón de inicio (requiere opciones de desarrollador activadas)";
        this.ENABLE_EMP_OPTIONS = "Activar opciones de Empleado";
        this.ENABLE_EMP_OPTIONS_DESC = "Desbloquear todas las opciones de Empleado usadas para pruebas";
        this.ALLOW_USER_NETWORK_CERTIFICATE = "Permitir certificado de red del usuario";
        this.ALLOW_USER_NETWORK_CERTIFICATE_DESC = "Permitir certificado de red del usuario para pruebas de whitehat";
        this.REMOVE_BUILD_EXPIRE_POPUP = "Desactivar panel emergente de versión caduca";
        this.REMOVE_BUILD_EXPIRE_POPUP_DESC = "Desactivar panel emergente de versión caduca que aparece cuando la versión de la aplicación envejece";
        this.EXPORT_DEV_OVERRIDES = "Exportar Copia de seguridad";
        this.IMPORT_DEV_OVERRIDES = "Importar Copia de seguridad";
        this.IMPORT_ID_MAPPING = "Importar archivos de mapeo";
        this.DOWNLOAD_ID_MAPPING = "Descargar archivos de mapeo";
		this.MISSING_MAPPING_FILE = "Archivos de mapeo no encontrados";
		this.MISSING_MAPPING_FILE_DESC = "Sin archivos de mapeo, las opciones de desarrollador no tendrán nombres. ¿Desea descargarlos?";

        this.CATEGORY_LINKS = "Enlaces";
        this.OPEN_LINKS_EXTERNALLY = "Abrir enlaces externamente";
        this.OPEN_LINKS_EXTERNALLY_DESC = "Abrir enlaces en un navegador externo en lugar del interno de la aplicación";
        this.SANITIZE_SHARE_LINKS = "Limpiar enlaces compartidos";

        this.CATEGORY_GHOST = "Fantasma";
        this.VIEW_STORIES_ANONYMOUSLY = "Ver historias anónimamente";
        this.VIEW_LIVE_ANONYMOUSLY = "Ver transmisiones anónimamente";
        this.DISABLE_TYPING_STATUS = "Desactivar estado de escritura";
        this.DISABLE_SCREENSHOT_DETECTION = "Desactivar detección de captura de pantalla";
        this.VIEW_DM_ANONYMOUSLY = "Ver mensajes directos anónimamente";
		this.TURN_ON_ALL_GHOST_MODES = "Activar todos los modos Fantasma";
		this.GHOST_MODES_ON = "Modo Fantasma: ON";
		this.GHOST_MODES_DEFAULT = "Modo Fantasma: POR DEFECTO";
		this.GHOST_MODES_QUICK_TOGGLE = "Activar cambio rápido para modos Fantasma";
		this.GHOST_MODES_QUICK_TOGGLE_DESC = "Agrega un boton para cambio rápido de modos Fantasma en la barra de acciones del chat";

        this.CATEGORY_DISTRACTION_FREE = "Libre de distracciones";
        this.DISABLE_STORIES = "Desactivar historias";
        this.DISABLE_HIGHLIGHTS = "Desactivar destacados";
        this.DISABLE_EXPLORE = "Desactivar explorar";
        this.DISABLE_COMMENTS = "Desactivar comentarios";
        this.LIMIT_FOLLOWING_FEED = "Limitar feed a perfiles seguidos";
        this.LIMIT_FOLLOWING_FEED_DESC = "Filtra el feed inicial para mostrar solo el contenido de perfiles que sigues.";
        this.DISABLE_REELS_SCROLLING = "Desactivar el scroll de Reels";
        this.DISABLE_REELS_SCROLLING_DESC =
                "Desactiva el scroll infinito de Reels de Instagram, impidiendo desplazarse al siguiente Reel. " +
                "Nota: En una instalación limpia, la animación de Consejo puede aparecer, pero desaparecerá luego de unos segundos.";
        this.HIDE_STORIES_TRAY = "Ocultar barra de historias";
        this.HIDE_STORIES_TRAY_DESC = "Oculta la barra de historias del feed principal";
        this.HIDE_NOTES_TRAY = "Ocultar barra de notas";
        this.HIDE_NOTES_TRAY_DESC = "Oculta la barra de notas en la sección de mensajes";
        this.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET = "Ocultar botón de creación de grupo en la hoja de compartir";
        this.DISABLE_DOUBLE_TAP_LIKE_POST = "Desactivar dar Me Gusta con doble toque en publicaciones";
        this.DISABLE_DOUBLE_TAP_LIKE_REEL = "Desactivar dar Me Gusta con doble toque en reels";
        this.DISABLE_DOUBLE_TAP_LIKE_COMMENT = "Desactivar dar Me Gusta con doble toque en comentarios";
        this.DISABLE_DOUBLE_TAP_LIKE_MESSAGE = "Desactivar dar Me Gusta con doble toque en mensajes";

        this.CATEGORY_MISC = "Otros";
        this.DISABLE_ANALYTICS = "Desactivar datos de análisis";
        this.DELETE_ANALYTICS_CACHE = "Borrar cache de datos de análisis";
        this.DISABLE_ANALYTICS_DESC = "Bloquear envió de datos de datos de análisis a servidores de Instagram/Facebook";
        this.DISABLE_DISCOVER_PEOPLE = "Desactivar descubrir personas";
        this.DISABLE_DISCOVER_PEOPLE_DESC = "Desactiva la sección descubrir personas del perfil de usuario";
        this.FOLLOW_BACK_INDICATOR = "Activar indicador de seguidores que también te siguen";
        this.FBI_FOLLOWS_YOU = "Te sigue";
        this.FBI_DOESNT_FOLLOWS_YOU = "No te sigue";
        this.VIEW_STORY_MENTIONS = "Ver menciones de historia";
        this.VSM_TITLE = "Menciones de historia";
        this.VSM_NO_MENTIONS = "Sin menciones en esta historia";
        this.DISABLE_STORY_FLIPPING = "Desactivar cambio de historias";
        this.DISABLE_STORY_FLIPPING_DESC = "Desactiva ir a la siguiente historia automaticamente";
        this.DISABLE_VIDEO_AUTOPLAY = "Desactivar reproducción automática de video";
        this.STORIES_AUDIO_AUTOPLAY = "Reproducción automática de audio en historias";
        this.CUSTOMISE_STORY_TIMESTAMP = "Personalizar marca de tiempo de historias";
        this.CUSTOMISE_STORY_TIMESTAMP_DESC = "Personaliza la marca de tiempo que muestra cuando se publicó la historia";
        this.UNLIMITED_REPLAYS = "Vuelve multimedia efímera en permanente";
        this.UNLIMITED_REPLAYS_DESC = "Transforma multimedia de una sola visualización en permanente al verla dos veces";
        this.IMPROVE_IMAGE_VIEWING = "Mejorar calidad de imagen";
        this.IMPROVE_IMAGE_VIEWING_DESC = "Obtiene la resolución máxima de las imágenes del servidor";
        this.HIDE_RESHARE_BUTTON = "Ocultar botón de reposteo";
        this.COPY_COMMENT = "Copiar comentario";
        this.COPY_COMMENT_DESC = "Agrega un botón para copiar comentarios en posts y reels";
        this.COMMENT_COPIED_SUCCESS = "Comentario copiado";
        this.COMMENT_COPIED_FAILED = "Sin texto encontrado para copiar";
		this.SAVE_MEDIA_COMMENT = "Guardar comentario multimedia";
		this.SAVE_MEDIA_COMMENT_DESC = "Agrega un botón para guardar los comentarios multimedias en publicaciones y reels";
        this.COPY_USERNAME = "Copiar nombre de usuario";
        this.COPY_FULL_NAME = "Copiar nombre completo";
        this.COPY_USER_ID = "Copiar ID de usuario";
        this.COPY_BIO = "Copiar bio";
        this.DOWNLOAD_PROFILE_PICTURE = "Descargar foto de perfil";
        this.COPIED = "Copiado";
        this.MORE_PROFILE_OPTIONS = "Más opciones de perfil";
		this.MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE = "Opciones de perfil en barra de acciones";
		this.MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE_DESC = "Mueve las opciones de perfil a la barra de acciones del perfil";
        this.REMOVE_EMPTY_BOTTOM_SPACE = "Quitar espacio inferior vacío";
        this.UNLOCK_PLUS_BENEFITS = "Desbloquear beneficios Plus";
        this.UNLOCK_PLUS_BENEFITS_DESC = "Desbloquea beneficios de suscripción 'Plus' verificados localmente. USAR BAJO TU PROPIO RIESGO";
        this.CUSTOMISE_STORY_RING_SIZE = "Personalizar tamaño del anillo de historias";
		this.CUSTOMISE_STORY_RING_SIZE_DESC = "Cambia el tamaño del anillo de las historias en un porcentaje (valor predeterminado 100)";

        this.CATEGORY_DOWNLOAD_MEDIA = "Descargar contenido multimedia";
        this.ENABLE_DOWNLOAD = "Activar descargas";
        this.ENABLE_DIRECT_DOWNLOAD = "Activar descargas directas";
        this.ENABLE_DIRECT_DOWNLOAD_DESC = "Descargar el archivo multimedia actual sin pedir opciones";
        this.DOWNLOAD_USERNAME_FOLDER = "Separar contenido multimedia por nombre de usuario";
        this.DOWNLOAD_USERNAME_FOLDER_DESC = "Crear subcarpetas basadas en los nombres de usuario";
        this.DOWNLOAD_CURRENT_MEDIA = "Descargar archivo actual";
        this.DOWNLOAD_AS_IMAGE = "Descargar como imagen";
		this.VIDEO_VARIANTS = "Variantes de video";
		this.IMAGE_VARIANTS = "Variantes de imagen";
        this.DOWNLOAD_AUDIO = "Descargar audio";
        this.DOWNLOAD_OPTIONS = "Opciones de descarga";
        this.COPY_MEDIA_LINK = "Copiar enlace del archivo";
        this.COPIED_MEDIA_LINK = "Enlace del archivo copiado";
        this.DOWNLOAD_ALL = "Descargar todo";
        this.DOWNLOADING_MEDIA = "Descargando : ";
        this.DOWNLOADED_MEDIA = "Descarga Completa : ";
        this.MEDIA_EXISTS = "Archivo ya existe";
        this.DOWNLOAD_FAILED_MEDIA = "Falla al descargar : ";
		this.DOWNLOAD_SET_PATH = "Establecer ruta de descarga";
		this.DOWNLOAD_SET_PATH_SUCCESS = "¡Directorio de descarga actualizado!";
		this.DOWNLOAD_SET_PATH_FAILED = "Falla al encontrar carpeta de destino";
		this.DOWNLOAD_GRANT_PERMISSION = "Por favor otorgue permisos de almacenamiento para continuar las descargas";
		this.DOWNLOAD_GRANT_PERMISSION_FAILED = "No se pudo abrir la configuración. Por favor otorgue manualmente permisos de Acceso a todos los archivos";

        this.POST_OPTIONS = "Opciones de publicaciones";
        this.COPY_POST_DESCRIPTION = "Copiar descripción de publicación";
        this.COPY_POST_OWNER_USERNAME = "Copiar usuario del dueño de la publicación";
        this.COPY_POST_OWNER_FULLNAME = "Copiar nombre completo del dueño de la publicación";
        this.ENABLE_MORE_OPTIONS_ON_POST = "Activar más opciones en publicaciones";
        this.ENABLE_MORE_OPTIONS_ON_POST_DESC = "Activa más opciones al mantener presionado sobre la publicación";

        this.CATEGORY_HIDE_NAVIGATION_BUTTONS = "Ocultar botones de navegación";
        this.HIDE_NAVIGATION_FEED = "Ocultar botón Feed";
        this.HIDE_NAVIGATION_REELS = "Ocultar botón Reels";
        this.HIDE_NAVIGATION_DIRECT = "Ocultar botón Transmisiones";
        this.HIDE_NAVIGATION_SEARCH = "Ocultar botón Búsqueda";
        this.HIDE_NAVIGATION_CREATE = "Ocultar botón Crear";

        this.PATCH_INFO_TITLE = "Información de Parches";
        this.EXPORT_PIKO_PREF = "Exportar ajustes de Piko";
        this.IMPORT_PIKO_PREF = "Importar ajustes de Piko";
        this.APP_VERSION = "Versión de aplicación: %s";
        this.PATCH_VERSION = "Versión de Parches: %s";

        this.EXPORT_SUCCESS = "Ajustes exportados correctamente";
        this.EXPORT_FAIL = "Falla al exportar";
        this.FAIL_NO_PATH = "Sin carpeta de destino asignada";
        this.FAIL_NO_FILE = "Archivo no encontrado";
        this.IMPORT_SUCCESS = "Ajustes importados correctamente";
        this.IMPORT_FAIL = "Falla al importar";
        this.RESTART_APP = "La aplicación necesita reiniciarse para aplicar los cambios establecidos";
        this.OK = "OK";
		this.CANCEL = "Cancelar";
        this.DELETED = "Borrado";
        this.PIKO_DEBUG = "Depuración de Piko";
        this.PIKO_DEBUG_DESC = "Agrega opciones de depuración en algunos componentes para pruebas";
        this.PIKO_EXPORT_EXPERIMENT_LIST = "Exportar lista de experimentos";
        this.PIKO_EXPORT_EXPERIMENT_MAPPINGS = "Exportar mapeo de experimentos";

        this.OPEN_IMAGE_EXTERNALLY = "Abrir imagen externamente";
        this.OPEN_VIDEO_EXTERNALLY = "Abrir video externamente";
        this.OPEN_IMAGE_WITH = "Abrir imagen con";
        this.OPEN_VIDEO_WITH = "Abrir video con";

        this.CHANGE_LIKE_ANIMATION = "Cambiar animación de Me Gusta";
        this.CHANGE_LIKE_ANIMATION_DESC = "Cambia la animación a una de Me gusta como la de los anillos existentes";
        this.AVAILABLE_LIKE_ANIMATION = "Animaciones de Me Gusta disponibles";

        this.DEFAULT = "Por Defecto";
        this.ARR_DETAILED_TIMESTAMP = "Marca de tiempo detallada";
        this.ARR_TIME_LEFT = "Tiempo restante";

        this.WELCOME_TITLE = "Bienvenido a Piko";
        this.WELCOME_MESSAGE = "Para mejorar su experiencia con Piko, por favor entre a la Configuración de Piko y ajuste las opciones a su gusto";
        this.GOTO_PIKO_SETTINGS = "Ir a Configuración de Piko";
        this.NO_INTERNET = "Por favor conéctese a internet";
		this.TAP_HERE = "¡¡ Toque aquí !!";
		this.PIKO_SETTINGS_ON_ACTION_BAR = "Configuración de Piko en barra de acciones";
		this.PIKO_SETTINGS_ON_ACTION_BAR_DESC = "Mueve el acceso a la Configuración de Piko hacia la barra de acciones del feed principal";
    }
          }
