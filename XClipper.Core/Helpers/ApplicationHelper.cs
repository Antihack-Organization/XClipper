﻿using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media.Animation;
using System.Windows.Threading;

namespace Components
{
    public static class ApplicationHelper
    {

        #region Constants

        const UInt32 SWP_NOSIZE = 0x0001;
        const UInt32 SWP_NOMOVE = 0x0002;
        const UInt32 SWP_SHOWWINDOW = 0x0040;

        #endregion

        #region Methods

        /// <summary>
        /// In order to perform synchronous operation asynchronously.
        /// </summary>
        public static void SendAction(Action action)
        {
            Application.Current.Dispatcher.BeginInvoke(action);
        }

        private static DispatcherTimer dtimer;
        private static bool isExecuted;
        /// <summary>
        /// This will provide a callback whenever the application process is not foreground.
        /// </summary>
        /// <param name="block"></param>
        public static void AttachForegroundProcess(Action block)
        {
            /** I do not support this logic, maybe in future I'll find a better solution. */
            dtimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(100) };
            dtimer.Tick += delegate
            {
                IntPtr handle = GetForegroundWindow();
                bool isActive = IsActivated(handle);
                if (!isActive)
                {
                    //  if (isExecuted) return;
                    // Debug.WriteLine("Deactivated()");
                    block.Invoke();
                    // isExecuted = true;
                }
                else isExecuted = false;
            };
            dtimer.Start();
        }

        /// <summary>Returns true if the current application has focus, false otherwise</summary>
        public static bool IsActivated(IntPtr hWnd)
        {
            var activatedHandle = hWnd;
            if (activatedHandle == IntPtr.Zero)
            {
                return false;       // No window is currently activated
            }
            var procId = Process.GetCurrentProcess().Id;
            int activeProcId;
            GetWindowThreadProcessId(activatedHandle, out activeProcId);

            return activeProcId == procId;
        }

        /// <summary>
        /// Activate a window from anywhere by attaching to the foreground window
        /// </summary>
        public static void GlobalActivate(this Window w)
        {
            if (dtimer != null) dtimer.Stop();

            var hWnd = new WindowInteropHelper(w).EnsureHandle();

            var currentForegroundWindow = GetForegroundWindow();
            var currentForegroundWindowThreadId = GetWindowThreadProcessId(currentForegroundWindow, IntPtr.Zero);
            var thisWindowThreadId = GetWindowThreadProcessId(hWnd, IntPtr.Zero);

            Thread.Sleep(70);

            w.Show();
            w.Activate();

            if (currentForegroundWindowThreadId != thisWindowThreadId)
            {
                AttachThreadInput(currentForegroundWindowThreadId, thisWindowThreadId, true);
                SetForegroundWindow(hWnd);
                AttachThreadInput(currentForegroundWindowThreadId, thisWindowThreadId, false);
            }
            else
            {
                SetForegroundWindow(hWnd);
            }

            Task.Run(async () =>
            {
                if (dtimer != null)
                {
                    await Task.Delay(300);
                    Application.Current.Dispatcher.Invoke(() => SetFocus(hWnd));
                    dtimer.Start();
                }
            });
        }

        /// <summary>
        /// Gets the Window title
        /// </summary>
        /// <returns></returns>
        public static string GetWindowTitle(IntPtr hWnd)
        {
            const int nChars = 256;
            StringBuilder Buff = new(nChars);

            if (GetWindowText(hWnd, Buff, nChars) > 0)
            {
                return Buff.ToString();
            }
            return null;
        }

        /// <summary>
        /// Returns the process name from the Handle
        /// </summary>
        public static string GetProcessName(IntPtr handle)
        {
            int processId;
            GetWindowThreadProcessId(handle, out processId);
            return $"{Process.GetProcessById(processId).ProcessName}.exe";
        }

        #endregion

        #region Imports

        [DllImport("user32.dll")]
        static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

        [DllImport("user32.dll", CharSet = CharSet.Auto, ExactSpelling = true)]
        internal static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        private static extern int GetWindowThreadProcessId(IntPtr handle, out int processId);

        [DllImport("user32.dll")]
        private static extern uint GetWindowThreadProcessId(IntPtr hWnd, IntPtr ProcessId);

        [DllImport("user32.dll")]
        private static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);

        [DllImport("user32.dll")]
        public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        public static extern IntPtr SetFocus(IntPtr hWnd);
        #endregion
    }
}
