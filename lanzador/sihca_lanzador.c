/*
 * SIHCA - Lanzador para Windows (SIHCA.exe)
 * -----------------------------------------
 * Ejecuta el aplicativo Java usando el entorno de ejecución incluido en la
 * carpeta "runtime" junto a este .exe, sin depender de rutas fijas ni de que
 * Java esté instalado en la computadora:
 *
 *     <carpeta del exe>\runtime\bin\javaw.exe -jar "<carpeta del exe>\app\sihca.jar"
 *
 * Si la carpeta runtime no existe, intenta con el Java instalado (javaw en el PATH).
 * Compilación (MinGW-w64):
 *     x86_64-w64-mingw32-windres recursos.rc -O coff -o recursos.res
 *     x86_64-w64-mingw32-gcc -municode -mwindows -O2 -s sihca_lanzador.c recursos.res -o SIHCA.exe
 */
#include <windows.h>
#include <wchar.h>

static void mostrar_error(const wchar_t *msg) {
    MessageBoxW(NULL, msg, L"SIHCA - No se pudo iniciar", MB_OK | MB_ICONERROR);
}

static BOOL existe(const wchar_t *ruta) {
    DWORD a = GetFileAttributesW(ruta);
    return a != INVALID_FILE_ATTRIBUTES;
}

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE hPrev, PWSTR cmd, int show) {
    wchar_t dir[MAX_PATH], java[MAX_PATH], jar[MAX_PATH], linea[3 * MAX_PATH];
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    wchar_t *p;

    (void)hInst; (void)hPrev; (void)cmd; (void)show;

    if (GetModuleFileNameW(NULL, dir, MAX_PATH) == 0) {
        mostrar_error(L"No se pudo determinar la carpeta del programa.");
        return 1;
    }
    p = wcsrchr(dir, L'\\');
    if (p) *p = L'\0';

    _snwprintf(jar, MAX_PATH, L"%ls\\app\\sihca.jar", dir);
    if (!existe(jar)) {
        mostrar_error(L"No se encontró app\\sihca.jar.\n\nCopie la carpeta SIHCA completa "
                      L"(app, config, runtime y sql) sin separar sus archivos.");
        return 1;
    }

    _snwprintf(java, MAX_PATH, L"%ls\\runtime\\bin\\javaw.exe", dir);
    if (existe(java)) {
        _snwprintf(linea, 3 * MAX_PATH, L"\"%ls\" -Dfile.encoding=UTF-8 -jar \"%ls\"", java, jar);
    } else {
        _snwprintf(linea, 3 * MAX_PATH, L"javaw.exe -Dfile.encoding=UTF-8 -jar \"%ls\"", jar);
    }

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(NULL, linea, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        mostrar_error(L"No se pudo ejecutar Java.\n\nVerifique que exista la carpeta \"runtime\" "
                      L"junto a SIHCA.exe, o instale Java 11 o superior.");
        return 1;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
