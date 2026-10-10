/* PSF CPython Android runtime, invoked as a private yt-dlp subprocess.
 * Keep stdout/stderr attached to Java's pipes, not Android's system logger.
 * This launcher adds no network access or certificate-validation exceptions.
 */
#include <Python.h>

int main(int argc, char **argv) {
    PyConfig config;
    PyConfig_InitPythonConfig(&config);
    config.write_bytecode = 0;
    config.user_site_directory = 0;
    PyStatus status = PyConfig_SetBytesArgv(&config, argc, argv);
    if (!PyStatus_Exception(status)) {
        status = Py_InitializeFromConfig(&config);
    }
    PyConfig_Clear(&config);
    if (PyStatus_Exception(status)) {
        Py_ExitStatusException(status);
    }
    return Py_RunMain();
}
