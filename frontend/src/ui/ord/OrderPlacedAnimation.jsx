const BOAT_GIF = "/boat-delivery.gif";

export default function OrderPlacedAnimation() {
  return (
    <div className="order-sail" role="status">
      <img src={BOAT_GIF} alt="" width="256" height="256" />
      <p>Preparing your order for isle delivery!</p>
    </div>
  );
}
