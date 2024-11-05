using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

public class TCPSocket : MonoBehaviour
{
    private TcpListener tcpListener;
    private Thread tcpListenerThread;
    private TcpClient connectedTcpClient;

    private Quaternion q;

    public GameObject targetPlane;
    public Color targetPlaneIdleColor = new Color(1, 1, 1, 0);
    public Color targetPlaneActiveColor = new Color(1, 1, 1, 50f / 255f);

    public GameObject targetObject;
    public Color targetObjectIdleColor = Color.white;
    public Color targetObjectSelectedColor = Color.black;

    private bool targetObjectSelected = false;
    private bool prevTargetObjectSelected = false;

    public Quaternion correction = Quaternion.identity;

    public GameObject mainVRCamera;
    public Vector3 cammeraCorrection = new Vector3(0, 0, 0);

    public string address = "192.168.0.28";
    public int port = 3207;

    public bool verbose = false;

    public enum AxisType
    {
        XYZ,
        XZY,
        YXZ,
        YZX,
        ZXY,
        ZYX
    };
    public AxisType axisType = AxisType.XYZ;

    public bool invertX = false;
    public bool invertY = false;
    public bool invertZ = false;

    public float dragX = 0;
    public float dragY = 0;
    public float dragMultiflier = 0.001f;
    public bool invertDragX = false;
    public bool invertDragY = false;

    Renderer targetPlaneColor;

    private bool unloadingTargetObject = false;

    void Awake()
    {
        if (verbose) Debug.Log("Start Server");
        // instance = this;

        // Start TcpServer background thread
        tcpListenerThread = new Thread(new ThreadStart(ListenForIncommingRequest));
        tcpListenerThread.IsBackground = true;
        tcpListenerThread.Start();
    }

    private void Start()
    {
        targetPlaneColor = targetPlane.GetComponent<Renderer>();
    }

    private void Update() {
        Vector3 cameraRotation = mainVRCamera.transform.rotation.eulerAngles;
        
        targetPlane.transform.rotation = q;
        targetPlane.transform.Rotate(correction.eulerAngles, Space.World);
        targetPlane.transform.Rotate(new Vector3(0, cameraRotation.y + cammeraCorrection.y, 0), Space.World);

        transform.rotation = q;
        transform.Rotate(correction.eulerAngles, Space.World);

        if (targetObjectSelected)
        {
            if (targetObjectSelected != prevTargetObjectSelected)
            {
                if (targetObject != null)
                {
                    Debug.Log("Target Selected");
                    targetPlane.transform.position = targetObject.transform.position;
                    targetPlaneColor.material.color = targetPlaneActiveColor;
                }
            }


            if (targetObject != null)
            {
                targetObject.transform.position = targetPlane.transform.position + targetPlane.transform.rotation * (new Vector3(-dragX * dragMultiflier, 0, dragY * dragMultiflier));
            }
        }
        else
        {
            if (targetObjectSelected != prevTargetObjectSelected)
            {
                if (targetObject != null)
                {
                    Debug.Log("Target Deselected");
                    targetPlaneColor.material.color = targetPlaneIdleColor;
                }
            }

            if (unloadingTargetObject)
            {
                targetObject = null;
                unloadingTargetObject = false;
            }
        }

        prevTargetObjectSelected = targetObjectSelected;
    }

    private void OnDestroy() {
        tcpListenerThread.Abort();
    }


    // Runs in background TcpServerThread; Handles incomming TcpClient requests
    private void ListenForIncommingRequest()
    {
        try
        {
            // Create tcp listener
            tcpListener = new TcpListener(IPAddress.Parse(address), port);
            tcpListener.Start();
            if (verbose) Debug.Log("Server is listening");

            while (true)
            {
                using (connectedTcpClient = tcpListener.AcceptTcpClient())
                {
                    // Get a stream object for reading
                    using (NetworkStream stream = connectedTcpClient.GetStream())
                    {
                        StreamReader streamReader = new StreamReader(stream);
                        StreamWriter streamWriter = new StreamWriter(stream);
                        // Read incomming stream into byte array.
                        while (connectedTcpClient.Connected)
                        {
                            string str = streamReader.ReadLine();
                            if (verbose) Debug.Log("received: " + str);

                            streamWriter.WriteLine(str);

                            float[] qs = new float[4];
                            string[] data = str.Split(',');
                            bool tf = true;

                            if (data[0] == "quaternion")
                            {
                                for (int i = 0; i < 4; i++)
                                {
                                    tf &= float.TryParse(data[i + 1], out qs[i]);
                                }

                                if (tf) {
                                    if (verbose) Debug.Log("received q: " + qs[0] + ", " + qs[1] + ", " + qs[2] + ", " + qs[3]);

                                    switch (axisType)
                                    {
                                        case AxisType.XYZ:
                                            (q.x, q.y, q.z, q.w) = (qs[0], qs[1], qs[2], qs[3]);
                                            break;
                                        case AxisType.XZY:
                                            (q.x, q.z, q.y, q.w) = (qs[0], qs[1], qs[2], qs[3]);
                                            break;
                                        case AxisType.YXZ:
                                            (q.y, q.x, q.z, q.w) = (qs[0], qs[1], qs[2], qs[3]);
                                            break;
                                        case AxisType.YZX:
                                            (q.y, q.z, q.x, q.w) = (qs[0], qs[1], qs[2], qs[3]);
                                            break;
                                        case AxisType.ZXY:
                                            (q.z, q.x, q.y, q.w) = (qs[0], qs[1], qs[2], qs[3]);
                                            break;
                                        case AxisType.ZYX:
                                            (q.z, q.y, q.x, q.w) = (qs[0], qs[1], qs[2], qs[3]);
                                            break;
                                    }

                                    if (invertX) q.x *= -1;
                                    if (invertY) q.y *= -1;
                                    if (invertZ) q.z *= -1;

                                }
                            }
                            else if (data[0] == "drag")
                            {
                                for (int i = 0; i < 2; i++)
                                {
                                    tf &= float.TryParse(data[i + 1], out qs[i]);
                                }
                                if (tf)
                                {
                                    if (verbose) Debug.Log("received drag: x: " + qs[0] + ", y: " + qs[1]);

                                    dragX += invertDragX ? qs[0] : -qs[0];
                                    dragY += invertDragY ? qs[1] : -qs[1];
                                }
                            }
                            else if (data[0] == "tap_start")
                            {
                                targetObjectSelected = true;
                            }
                            else if (data[0] == "tap_end")
                            {
                                targetObjectSelected = false;

                                dragX = 0;
                                dragY = 0;
                            }
                            else if (data[0] == "correction")
                            {
                                Vector3 v = q.eulerAngles;
                                correction = Quaternion.Euler(-v.x, -v.y, -v.z);
                            }
                        }
                        if (verbose) Debug.Log("Client disconnected");
                    }
                }
            }
        }
        catch (SocketException socketException)
        {
            if (verbose) Debug.Log("SocketException " + socketException.ToString());
        }
    }

    public void LoadTargetObject(GameObject gameObject)
    {
        this.targetObject = gameObject;
    }

    public void UnloadTargetObject()
    {
        this.unloadingTargetObject = true;
    }
}