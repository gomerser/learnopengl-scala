package learnopengl

import org.joml.{Matrix4f, Vector3f}

object CameraMovement extends Enumeration {
  val FORWARD, BACKWARD, LEFT, RIGHT = Value
}

class Camera(
    var position: Vector3f = new Vector3f(0f, 0f, 0f),
    var worldUp: Vector3f = new Vector3f(0f, 1f, 0f),
    var yaw: Float = -90f,
    var pitch: Float = 0f
) {

  // camera attributes
  var front: Vector3f = new Vector3f(0f, 0f, -1f)
  var right: Vector3f = new Vector3f()
  var up: Vector3f = new Vector3f()

  // camera options
  var movementSpeed: Float = 2.5f
  var mouseSensitivity: Float = 0.1f
  var zoom: Float = 45f

  // initialize camera vectors
  updateCameraVectors()

  def getViewMatrix: Matrix4f =
    new Matrix4f().lookAt(position, new Vector3f(position).add(front), up)

  def processKeyboard(direction: CameraMovement.Value, deltaTime: Float): Unit = {
    val velocity = movementSpeed * deltaTime
    direction match {
      case CameraMovement.FORWARD  => position.fma(velocity, front)
      case CameraMovement.BACKWARD => position.fma(-velocity, front)
      case CameraMovement.LEFT     => position.fma(-velocity, new Vector3f(front).cross(worldUp).normalize())
      case CameraMovement.RIGHT    => position.fma(velocity, new Vector3f(front).cross(worldUp).normalize())
    }
  }

  def processMouseMovement(xoffset: Float, yoffset: Float, constrainPitch: Boolean = true): Unit = {
    val xoff = xoffset * mouseSensitivity
    val yoff = yoffset * mouseSensitivity

    yaw += xoff
    pitch += yoff

    if (constrainPitch) {
      if (pitch > 89f) pitch = 89f
      if (pitch < -89f) pitch = -89f
    }

    updateCameraVectors()
  }

  def processMouseScroll(yoffset: Float): Unit = {
    zoom -= yoffset
    if (zoom < 1f) zoom = 1f
    if (zoom > 45f) zoom = 45f
  }

  private def updateCameraVectors(): Unit = {
    val f = new Vector3f(
      (Math.cos(Math.toRadians(yaw.toDouble)) * Math.cos(Math.toRadians(pitch.toDouble))).toFloat,
      Math.sin(Math.toRadians(pitch.toDouble)).toFloat,
      (Math.sin(Math.toRadians(yaw.toDouble)) * Math.cos(Math.toRadians(pitch.toDouble))).toFloat
    )
    front = f.normalize()
    right = new Vector3f(front).cross(worldUp).normalize()
    up = new Vector3f(right).cross(front).normalize()
  }
}
