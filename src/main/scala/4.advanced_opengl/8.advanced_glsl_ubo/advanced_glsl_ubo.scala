package learnopengl_4_8

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader_m.Shader
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 3.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
var lastFrame: Float = 0.0f

val sizeofMatrix4f: Long = 16 * java.lang.Float.BYTES // 64 bytes

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
  GLFWErrorCallback.createPrint(System.err).set()
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

  if (System.getProperty("os.name").toLowerCase.contains("mac"))
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

  // glfw window creation
  // --------------------
  val window = glfwCreateWindow(SCR_WIDTH, SCR_HEIGHT, "LearnOpenGL", 0, 0)
  if (window == 0L) {
    glfwTerminate()
    throw new RuntimeException("Failed to create GLFW window")
  }
  glfwMakeContextCurrent(window)
  glfwSetFramebufferSizeCallback(window, framebuffer_size_callback)
  glfwSetCursorPosCallback(window, mouse_callback)

  // tell GLFW to capture our mouse
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile shaders
  // -------------------------
  val shaderRed =
    Shader("4.advanced_opengl/8.advanced_glsl.vs", "4.advanced_opengl/8.red.fs")
  val shaderGreen = Shader(
    "4.advanced_opengl/8.advanced_glsl.vs",
    "4.advanced_opengl/8.green.fs"
  )
  val shaderBlue = Shader(
    "4.advanced_opengl/8.advanced_glsl.vs",
    "4.advanced_opengl/8.blue.fs"
  )
  val shaderYellow = Shader(
    "4.advanced_opengl/8.advanced_glsl.vs",
    "4.advanced_opengl/8.yellow.fs"
  )

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val cubeVertices: Array[Float] = Array(
    // positions
    -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
    -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f,
    -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
    -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f,
    -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
    0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f,
    -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
    0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
    -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
    0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f
  )
  // cube VAO
  val cubeVAO = glGenVertexArrays()
  val cubeVBO = glGenBuffers()
  glBindVertexArray(cubeVAO)
  glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
  val cubeVertexBuf = MemoryUtil.memAllocFloat(cubeVertices.length)
  cubeVertexBuf.put(cubeVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, cubeVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(cubeVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0L)

  // configure a uniform buffer object
  // ---------------------------------
  // first. We get the relevant block indices
  val uniformBlockIndexRed = glGetUniformBlockIndex(shaderRed.ID, "Matrices")
  val uniformBlockIndexGreen =
    glGetUniformBlockIndex(shaderGreen.ID, "Matrices")
  val uniformBlockIndexBlue = glGetUniformBlockIndex(shaderBlue.ID, "Matrices")
  val uniformBlockIndexYellow =
    glGetUniformBlockIndex(shaderYellow.ID, "Matrices")
  // then we link each shader's uniform block to this uniform binding point
  glUniformBlockBinding(shaderRed.ID, uniformBlockIndexRed, 0)
  glUniformBlockBinding(shaderGreen.ID, uniformBlockIndexGreen, 0)
  glUniformBlockBinding(shaderBlue.ID, uniformBlockIndexBlue, 0)
  glUniformBlockBinding(shaderYellow.ID, uniformBlockIndexYellow, 0)
  // Now actually create the buffer
  val uboMatrices = glGenBuffers()
  glBindBuffer(GL_UNIFORM_BUFFER, uboMatrices)
  glBufferData(GL_UNIFORM_BUFFER, 2 * sizeofMatrix4f, GL_STATIC_DRAW)
  glBindBuffer(GL_UNIFORM_BUFFER, 0)
  // define the range of the buffer that links to a uniform binding point
  glBindBufferRange(GL_UNIFORM_BUFFER, 0, uboMatrices, 0, 2 * sizeofMatrix4f)

  // store the projection matrix (we only do this once now) (note: we're not using zoom anymore by changing the FoV)
  val projection = new Matrix4f().perspective(
    45.0f,
    SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
    0.1f,
    100.0f
  )
  glBindBuffer(GL_UNIFORM_BUFFER, uboMatrices)
  val projBuffer = MemoryUtil.memAllocFloat(16)
  projection.get(projBuffer)
  glBufferSubData(GL_UNIFORM_BUFFER, 0, projBuffer)
  MemoryUtil.memFree(projBuffer)
  glBindBuffer(GL_UNIFORM_BUFFER, 0)

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {

    // per-frame time logic
    // --------------------
    val currentFrame = glfwGetTime().toFloat
    deltaTime = currentFrame - lastFrame
    lastFrame = currentFrame

    // input
    // -----
    processInput(window)

    // render
    // ------
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // set the view and projection matrix in the uniform block - we only have to do this once per loop iteration.
    val view = camera.getViewMatrix
    val viewBuf = MemoryUtil.memAllocFloat(16)
    view.get(viewBuf)
    glBindBuffer(GL_UNIFORM_BUFFER, uboMatrices)
    glBufferSubData(GL_UNIFORM_BUFFER, sizeofMatrix4f, viewBuf)
    glBindBuffer(GL_UNIFORM_BUFFER, 0)
    MemoryUtil.memFree(viewBuf)

    // draw 4 cubes
    // RED
    shaderRed.use()
    var model = Matrix4f().identity()
    model.translate(-0.75f, 0.75f, 0.0f)
    shaderRed.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    // GREEN
    shaderGreen.use()
    model = Matrix4f().identity()
    model.translate(0.75f, 0.75f, 0.0f)
    shaderGreen.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    // YELLOW
    shaderYellow.use()
    model = Matrix4f().identity()
    model.translate(-0.75f, -0.75f, 0.0f)
    shaderYellow.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    // BLUE
    shaderBlue.use()
    model = Matrix4f().identity()
    model.translate(0.75f, -0.75f, 0.0f)
    shaderBlue.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(cubeVAO)
  glDeleteBuffers(cubeVBO)

  glfwTerminate()

// process all input: query GLFW whether relevant keys are pressed/released this frame and react accordingly
// ---------------------------------------------------------------------------------------------------------
def processInput(window: Long): Unit = {
  if glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS then
    glfwSetWindowShouldClose(window, true)

  if glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS then
    camera.processKeyboard(FORWARD, deltaTime)
  if glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS then
    camera.processKeyboard(BACKWARD, deltaTime)
  if glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS then
    camera.processKeyboard(LEFT, deltaTime)
  if glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS then
    camera.processKeyboard(RIGHT, deltaTime)
}

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}

// glfw: whenever the mouse moves, this callback is called
// -------------------------------------------------------
def mouse_callback(window: Long, xposIn: Double, yposIn: Double): Unit = {
  val xpos = xposIn.toFloat
  val ypos = yposIn.toFloat

  if (firstMouse) {
    lastX = xpos
    lastY = ypos
    firstMouse = false
  }

  var xoffset = xpos - lastX
  var yoffset =
    lastY - ypos // reversed since y-coordinates go from bottom to top
  lastX = xpos
  lastY = ypos

  camera.processMouseMovement(xoffset, yoffset)
}
