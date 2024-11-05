using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using Tobii.G2OM; // Using Tobii XR for eye tracking

public class SelectAtGaze : MonoBehaviour, IGazeFocusable
{
    public GameObject TCPSocket;
    TCPSocket _TCPSocket;

    void Start()
    {
        _TCPSocket = TCPSocket.GetComponent<TCPSocket>();    
    }

    void Update()
    {
        
    }

    //The method of the "IGazeFocusable" interface, which will be called when this object receives or loses focus
    public void GazeFocusChanged(bool hasFocus)
    {
        if (hasFocus)
        {
            // Called when this object received focus
            _TCPSocket.LoadTargetObject(this.gameObject);
        }
        else
        {
            // Called when this object lost focus
            _TCPSocket.UnloadTargetObject();
        }
    }
}
